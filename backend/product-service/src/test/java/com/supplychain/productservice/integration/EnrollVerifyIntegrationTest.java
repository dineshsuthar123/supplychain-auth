package com.supplychain.productservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.productservice.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EnrollVerifyIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("blockchain.enabled", () -> "false");
        registry.add("supplyprint.auth.allow-self-service-operator-roles", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;

    @Test
    void productionSignupOptionsAndRegistrationRequireOperatorApproval() throws Exception {
        mvc.perform(get("/auth/registration-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
        String username = "signup-test-" + UUID.randomUUID();
        String signupPassword = UUID.randomUUID().toString();
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username,
                                "email", username + "@example.test", "password", signupPassword,
                                "role", "MANUFACTURER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void multipartRetakeAddsAnotherRecordReadableByAnAuditor() throws Exception {
        UUID tenant = createTenant();
        String manufacturer = token(tenant, "MANUFACTURER");
        String auditor = token(tenant, "AUDITOR");
        String product = "GEAR-" + UUID.randomUUID();
        MockMultipartFile image = image();

        mvc.perform(multipart("/api/enroll/image").file(image).param("productId", product)
                        .param("metadata", "{\"source\":\"fixture\"}").header("Authorization", manufacturer))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.productId").value(product))
                .andExpect(jsonPath("$.featureHash").isString());

        mvc.perform(multipart("/api/enroll/image").file(image).param("productId", product)
                        .header("Authorization", manufacturer)).andExpect(status().isConflict());

        String first = capture(product, manufacturer, image);
        String retake = capture(product, manufacturer, image);
        assertThat(first).isNotBlank().isNotEqualTo(retake);

        mvc.perform(multipart("/api/verify/image").file(new MockMultipartFile("image", new byte[]{1, 2}))
                        .param("productId", product).header("Authorization", manufacturer)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/verify/image").file(image).param("productId", product)
                        .header("Authorization", auditor)).andExpect(status().isForbidden());

        mvc.perform(get("/api/verify/{id}/log", product).header("Authorization", auditor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts.length()").value(2))
                .andExpect(jsonPath("$.attempts[0].attemptId").value(retake))
                .andExpect(jsonPath("$.attempts[1].attemptId").value(first))
                .andExpect(jsonPath("$.attempts[1].decision").value("MATCH"))
                .andExpect(jsonPath("$.attempts[1].createdAt").isString())
                .andExpect(jsonPath("$.ledgerStatus").value("PENDING"));
        mvc.perform(get("/api/dashboard").header("Authorization", auditor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verificationsToday").value(2));

        String other = token(createTenant(), "MANUFACTURER");
        mvc.perform(get("/api/verify/log").param("productId", product).header("Authorization", other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.attempts.length()").value(0));
        mvc.perform(multipart("/api/verify/image").file(image).param("productId", product).header("Authorization", other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("UNKNOWN_PRODUCT"))
                .andExpect(jsonPath("$.confidence").isEmpty());
        mvc.perform(get("/api/verify/log").param("productId", product).header("Authorization", other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.attempts[0].decision").value("UNKNOWN_PRODUCT"))
                .andExpect(jsonPath("$.featureHash").doesNotExist());
    }

    @Test
    void oldEventsKeepTheirKnownFactsAndPaginate() throws Exception {
        UUID tenant = createTenant();
        String auditor = token(tenant, "AUDITOR");
        String product = "ARCHIVE/" + UUID.randomUUID();
        for (int i = 0; i < 21; i++) {
            jdbc.update("""
                    INSERT INTO verification_events (tenant_id, product_id, verified, similarity)
                    VALUES (?, ?, false, 0)
                    """, tenant, product);
        }
        mvc.perform(get("/api/verify/log").param("productId", product).header("Authorization", auditor))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.attempts.length()").value(20))
                .andExpect(jsonPath("$.hasMoreAttempts").value(true));
        mvc.perform(get("/api/verify/log").param("productId", product).param("page", "1").header("Authorization", auditor))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.attempts.length()").value(1))
                .andExpect(jsonPath("$.attempts[0].verified").value(false))
                .andExpect(jsonPath("$.attempts[0].decision").isEmpty())
                .andExpect(jsonPath("$.attempts[0].confidence").isEmpty())
                .andExpect(jsonPath("$.hasMoreAttempts").value(false));
    }

    private UUID createTenant() {
        UUID tenant = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name) VALUES (?, ?)", tenant, tenant.toString());
        return tenant;
    }

    private String token(UUID tenant, String role) {
        return "Bearer " + jwt.generateAccessToken(UUID.randomUUID(), tenant, "operator@example.test", role);
    }

    private String capture(String product, String authorization, MockMultipartFile image) throws Exception {
        String response = mvc.perform(multipart("/api/verify/image").file(image).param("productId", product)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.decision").value("MATCH"))
                .andExpect(jsonPath("$.confidence").isNumber())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("attemptId").asText();
    }

    private static MockMultipartFile image() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(0, 0, 100, 100);
        graphics.setColor(Color.LIGHT_GRAY);
        graphics.fillOval(10, 10, 80, 80);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return new MockMultipartFile("image", "fixture.png", "image/png", output.toByteArray());
    }
}
