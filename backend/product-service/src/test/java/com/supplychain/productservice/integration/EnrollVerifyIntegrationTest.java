package com.supplychain.productservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supplychain.productservice.dto.EnrollRequest;
import com.supplychain.productservice.dto.VerifyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test: spins up a real pgvector container, runs Flyway migrations,
 * then exercises the enroll → verify flow end-to-end.
 *
 * Uses the {@code dev} profile so no ONNX model or blockchain wallet is required.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
class EnrollVerifyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("supplyprint_test")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("blockchain.enabled",         () -> "false");
    }

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @Test
    void enroll_thenVerify_returnsVerifiedTrue() throws Exception {
        String productId = "TEST-INTEGRATION-001";
        List<Double> embedding = syntheticEmbedding(1.0);

        // ── Enroll ──────────────────────────────────────────────────────────
        String enrollBody = objectMapper.writeValueAsString(
                new EnrollRequest(productId, "{\"test\":true}", embedding));

        mockMvc.perform(post("/api/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.featureHash").isString());

        // ── Verify with same embedding (similarity should be 1.0) ───────────
        String verifyBody = objectMapper.writeValueAsString(
                new VerifyRequest(productId, embedding));

        mockMvc.perform(post("/api/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.confidence").isNumber());
    }

    @Test
    void enroll_duplicate_returns409() throws Exception {
        String productId = "TEST-DUP-001";
        List<Double> embedding = syntheticEmbedding(2.0);
        String body = objectMapper.writeValueAsString(
                new EnrollRequest(productId, null, embedding));

        // First enroll
        mockMvc.perform(post("/api/enroll").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        // Second enroll → 409
        mockMvc.perform(post("/api/enroll").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.productId").value(productId));
    }

    @Test
    void verify_unknownProduct_returnsNotVerified() throws Exception {
        String body = objectMapper.writeValueAsString(
                new VerifyRequest("UNKNOWN-PRODUCT", syntheticEmbedding(3.0)));

        mockMvc.perform(post("/api/verify").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void enrollmentLog_unknownProduct_returns404() throws Exception {
        mockMvc.perform(get("/api/verify/NONEXISTENT-PRODUCT/log"))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<Double> syntheticEmbedding(double seed) {
        List<Double> emb = new ArrayList<>(128);
        double norm = 0.0;
        for (int i = 0; i < 128; i++) {
            double v = Math.sin(seed * (i + 1));
            emb.add(v);
            norm += v * v;
        }
        // L2-normalise
        double n = Math.sqrt(norm);
        for (int i = 0; i < 128; i++) emb.set(i, emb.get(i) / n);
        return emb;
    }
}
