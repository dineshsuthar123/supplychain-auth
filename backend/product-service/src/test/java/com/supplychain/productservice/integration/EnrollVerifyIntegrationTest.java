package com.supplychain.productservice.integration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.productservice.ai.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("oracle") @Testcontainers
class EnrollVerifyIntegrationTest {
 static final String ORIGIN="https://supplyprint.test", PASSWORD="test-account-only-long-password";
 @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:0.8.2-pg16-bookworm@sha256:00ba258a66dac104fd5171074a0084462a64a1369d8513f3d0a634e2f24d15bc").asCompatibleSubstituteFor("postgres")).withDatabaseName("supplyprint_test").withUsername("postgres").withPassword("isolated-admin-only").withInitScript("oracle-bootstrap.sql");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.password",()->"test-runtime-only-very-long-password");r.add("spring.flyway.password",()->"test-migration-only-very-long-password");r.add("jwt.secret",()->"isolated-test-signing-key-only-abcdefghijklmnopqrstuvwxyz");r.add("supplyprint.cors.allowed-origins",()->ORIGIN);}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired EmbeddingService embedding;
 @Autowired ApplicationContext context;
 @Test void realOnnxHas128FiniteNormalisedDimensions() throws Exception {
  assertThat(embedding).isInstanceOf(OnnxEmbeddingService.class);float[] values=embedding.getEmbedding(sample());assertThat(values).hasSize(128);double norm=0;for(float v:values){assertThat(Float.isFinite(v)).isTrue();norm+=v*v;}assertThat(norm).isCloseTo(1.0,within(0.0001));assertThat(embedding.getEmbedding(sample())).containsExactly(values);
 }
 @Test void realImageFlowIsTenantScoped() throws Exception {
  String token=operator("maker"),product="IMAGE-"+UUID.randomUUID();
  mvc.perform(multipart("/api/enroll/image").file(file()).param("productId",product).header("Authorization","Bearer "+token)).andExpect(status().isAccepted());
  mvc.perform(multipart("/api/verify/image").file(file()).param("productId",product).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true)).andExpect(jsonPath("$.blockchainConfirmed").value(false));
  mvc.perform(get("/api/verify/"+product+"/log").header("Authorization","Bearer "+token)).andExpect(status().isOk());
  String other=operator("other");mvc.perform(get("/api/verify/"+product+"/log").header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
  mvc.perform(multipart("/api/verify/image").file(file()).param("productId",product).header("Authorization","Bearer "+other)).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(false));
  assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM product_fingerprints WHERE product_id=?",Integer.class,product)).isEqualTo(128);
 }
 @Test void signupCannotChoosePrivilegesAndRefreshRotates() throws Exception {
  String suffix=UUID.randomUUID().toString().replace("-","");
  var result=mvc.perform(post("/auth/register").header("Origin",ORIGIN).contentType("application/json").content(json.writeValueAsString(Map.of("email",suffix+"@example.test","username","u"+suffix,"password",PASSWORD,"role","ADMIN")))).andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("USER")).andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
  var cookie=result.getResponse().getCookie("refresh");assertThat(cookie).isNotNull();assertThat(cookie.isHttpOnly()).isTrue();assertThat(cookie.getSecure()).isTrue();String first=cookie.getValue();
  var rotated=mvc.perform(post("/auth/refresh").header("Origin",ORIGIN).cookie(cookie)).andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh");assertThat(rotated.getValue()).isNotEqualTo(first);
  mvc.perform(post("/auth/refresh").header("Origin",ORIGIN).cookie(cookie)).andExpect(status().isUnauthorized());
 }
 @Test void unsafeRoutesOriginsAndMetadataPrivilegesAreBlocked() throws Exception {
  assertThat(context.containsBean("godModeController")).isFalse();
  mvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());mvc.perform(post("/auth/login").contentType("application/json").content("{}")).andExpect(status().isForbidden());
  for(String path:List.of("/auth/siwe/nonce","/swagger-ui/index.html","/actuator/prometheus","/api/godmode/products"))mvc.perform(get(path)).andExpect(status().isNotFound());
  assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,'flyway_schema_history','UPDATE')",Boolean.class)).isFalse();assertThat(jdbc.queryForObject("SELECT has_schema_privilege(current_user,'public','CREATE')",Boolean.class)).isFalse();
  mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.components").doesNotExist());
  mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
 }
 String operator(String label) throws Exception {
  String suffix=UUID.randomUUID().toString().replace("-",""),email=label+suffix+"@example.test";
  mvc.perform(post("/auth/register").header("Origin",ORIGIN).contentType("application/json").content(json.writeValueAsString(Map.of("email",email,"username",label+suffix,"password",PASSWORD)))).andExpect(status().isCreated());
  jdbc.update("UPDATE users SET role='MANUFACTURER' WHERE email=?",email);
  String body=mvc.perform(post("/auth/login").header("Origin",ORIGIN).contentType("application/json").content(json.writeValueAsString(Map.of("emailOrUsername",email,"password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(body).get("accessToken").asText();
 }
 byte[] sample() throws Exception {return getClass().getResourceAsStream("/oracle/sample.png").readAllBytes();}
 MockMultipartFile file() throws Exception {return new MockMultipartFile("image","sample.png","image/png",sample());}
}
