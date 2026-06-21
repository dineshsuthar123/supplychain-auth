package com.supplychain.productservice.service;

import com.supplychain.productservice.dto.EnrollRequest;
import com.supplychain.productservice.dto.EnrollResponse;
import com.supplychain.productservice.entity.BlockchainOutbox;
import com.supplychain.productservice.entity.ProductFingerprint;
import com.supplychain.productservice.exception.DuplicateProductException;
import com.supplychain.productservice.repository.BlockchainOutboxRepository;
import com.supplychain.productservice.repository.ProductFingerprintRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FingerprintEnrollmentServiceTest {

    @Mock ProductFingerprintRepository fingerprintRepo;
    @Mock BlockchainOutboxRepository   outboxRepo;

    FingerprintEnrollmentService service;

    @BeforeEach
    void setUp() {
        service = new FingerprintEnrollmentService(fingerprintRepo, outboxRepo, new SimpleMeterRegistry());
    }

    @Test
    void enroll_savesFingerprint_andQueuesOutbox() {
        given(fingerprintRepo.existsByProductId("P001")).willReturn(false);
        given(fingerprintRepo.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(outboxRepo.save(any())).willAnswer(inv -> inv.getArgument(0));

        EnrollRequest req = new EnrollRequest("P001", "{\"sku\":\"X\"}", embedding128());
        EnrollResponse resp = service.enroll(req);

        assertThat(resp.productId()).isEqualTo("P001");
        assertThat(resp.status()).isEqualTo("PENDING");
        assertThat(resp.featureHash()).hasSize(64); // SHA-256 = 32 bytes = 64 hex chars

        verify(fingerprintRepo).save(any(ProductFingerprint.class));
        verify(outboxRepo).save(any(BlockchainOutbox.class));
    }

    @Test
    void enroll_throwsDuplicate_whenProductAlreadyExists() {
        given(fingerprintRepo.existsByProductId("P001")).willReturn(true);
        assertThatThrownBy(() -> service.enroll(new EnrollRequest("P001", null, embedding128())))
                .isInstanceOf(DuplicateProductException.class)
                .hasMessageContaining("P001");
    }

    @Test
    void computeFeatureHash_isDeterministic() {
        List<Double> emb = embedding128();
        String h1 = FingerprintEnrollmentService.computeFeatureHash("P001", emb);
        String h2 = FingerprintEnrollmentService.computeFeatureHash("P001", emb);
        assertThat(h1).isEqualTo(h2).hasSize(64);
    }

    @Test
    void computeFeatureHash_differsForDifferentProductIds() {
        List<Double> emb = embedding128();
        String h1 = FingerprintEnrollmentService.computeFeatureHash("P001", emb);
        String h2 = FingerprintEnrollmentService.computeFeatureHash("P002", emb);
        assertThat(h1).isNotEqualTo(h2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<Double> embedding128() {
        Double[] arr = new Double[128];
        for (int i = 0; i < 128; i++) arr[i] = (double) i / 128.0;
        return List.of(arr);
    }
}
