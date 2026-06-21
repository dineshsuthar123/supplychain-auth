package com.supplychain.productservice.service;

import com.supplychain.productservice.blockchain.BlockchainService;
import com.supplychain.productservice.dto.VerifyRequest;
import com.supplychain.productservice.dto.VerifyResponse;
import com.supplychain.productservice.entity.ProductFingerprint;
import com.supplychain.productservice.repository.ProductFingerprintRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FingerprintVerificationServiceTest {

    @Mock ProductFingerprintRepository fingerprintRepo;
    @Mock BlockchainService            blockchainService;

    FingerprintVerificationService service;

    @BeforeEach
    void setUp() {
        service = new FingerprintVerificationService(fingerprintRepo, blockchainService, new SimpleMeterRegistry());
        // Inject the threshold field (normally set via @Value)
        injectThreshold(0.85);
    }

    @Test
    void verify_returnsNotFound_whenNoRecord() {
        given(fingerprintRepo.findClosestByProductId(anyString(), anyString()))
                .willReturn(List.of());

        VerifyResponse resp = service.verify(new VerifyRequest("P999", embedding128()));

        assertThat(resp.verified()).isFalse();
        assertThat(resp.warning()).isNotBlank();
    }

    @Test
    void verify_returnsBelowThreshold_whenSimilarityLow() {
        Object[] row = buildRow(0.60);
        given(fingerprintRepo.findClosestByProductId(anyString(), anyString()))
                .willReturn(List.of(row));

        VerifyResponse resp = service.verify(new VerifyRequest("P001", embedding128()));

        assertThat(resp.verified()).isFalse();
        assertThat(resp.confidence()).isEqualTo(0.60);
    }

    @Test
    void verify_returnsVerifiedWithBlockchain_whenAboveThreshold() throws Exception {
        Object[] row = buildRow(0.97);
        given(fingerprintRepo.findClosestByProductId(anyString(), anyString()))
                .willReturn(List.of(row));

        ProductFingerprint fp = new ProductFingerprint("P001", new float[128], "abc123", null);
        fp.setTransactionHash("0xdeadbeef");
        fp.setBlockNumber(12345L);
        given(fingerprintRepo.findByProductId("P001")).willReturn(Optional.of(fp));
        given(blockchainService.verifyOnChain("P001", "abc123")).willReturn(true);
        given(blockchainService.isBlockchainEnabled()).willReturn(true);

        VerifyResponse resp = service.verify(new VerifyRequest("P001", embedding128()));

        assertThat(resp.verified()).isTrue();
        assertThat(resp.blockchainConfirmed()).isTrue();
        assertThat(resp.confidence()).isEqualTo(0.97);
        assertThat(resp.transactionHash()).isEqualTo("0xdeadbeef");
    }

    @Test
    void verify_degradesGracefully_whenBlockchainUnreachable() throws Exception {
        Object[] row = buildRow(0.95);
        given(fingerprintRepo.findClosestByProductId(anyString(), anyString()))
                .willReturn(List.of(row));

        ProductFingerprint fp = new ProductFingerprint("P001", new float[128], "abc123", null);
        given(fingerprintRepo.findByProductId("P001")).willReturn(Optional.of(fp));
        given(blockchainService.verifyOnChain(anyString(), anyString()))
                .willReturn(null); // circuit open fallback
        given(blockchainService.isBlockchainEnabled()).willReturn(true);

        VerifyResponse resp = service.verify(new VerifyRequest("P001", embedding128()));

        assertThat(resp.verified()).isTrue();
        assertThat(resp.blockchainConfirmed()).isFalse();
        assertThat(resp.warning()).isNotBlank();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Inject private @Value field via reflection for unit test */
    private void injectThreshold(double threshold) {
        try {
            var field = FingerprintVerificationService.class.getDeclaredField("similarityThreshold");
            field.setAccessible(true);
            field.set(service, threshold);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Build a synthetic native query result row where the last column is the similarity score.
     * The real query returns all columns of product_fingerprints plus similarity at the end.
     */
    private static Object[] buildRow(double similarity) {
        // We only care about the last element (similarity); null the rest
        return new Object[]{null, null, null, null, null, null, null, null, similarity};
    }

    private static List<Double> embedding128() {
        Double[] arr = new Double[128];
        for (int i = 0; i < 128; i++) arr[i] = (double) i / 128.0;
        return List.of(arr);
    }
}
