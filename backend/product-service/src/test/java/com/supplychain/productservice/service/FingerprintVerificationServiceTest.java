package com.supplychain.productservice.service;

import com.supplychain.productservice.blockchain.BlockchainService;
import com.supplychain.productservice.dto.VerifyRequest;
import com.supplychain.productservice.dto.VerifyResponse.Decision;
import com.supplychain.productservice.repository.ProductFingerprintRepository;
import com.supplychain.productservice.repository.VerificationEventRepository;
import com.supplychain.productservice.tenant.TenantContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FingerprintVerificationServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final VerifyRequest request = new VerifyRequest("P001", Collections.nCopies(128, 0.125));

    @Mock ProductFingerprintRepository fingerprints;
    @Mock VerificationEventRepository attempts;
    @Mock BlockchainService blockchain;

    FingerprintVerificationService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        service = new FingerprintVerificationService(fingerprints, attempts, blockchain, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.85);
        given(attempts.save(any())).willAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void missingFingerprintHasNoScore() {
        var response = service.verify(request);
        assertThat(response.decision()).isEqualTo(Decision.UNKNOWN_PRODUCT);
        assertThat(response.verified()).isFalse();
        assertThat(response.confidence()).isNull();
    }

    @Test
    void lowSimilarityPreservesAnActualZeroScore() {
        candidate(0, null, null);
        var response = service.verify(request);
        assertThat(response.decision()).isEqualTo(Decision.NO_MATCH);
        assertThat(response.verified()).isFalse();
        assertThat(response.confidence()).isZero();
    }

    @Test
    void matchCanSucceedWithoutLedgerConfirmation() {
        candidate(0.97, null, null);
        var response = service.verify(request);
        assertThat(response.decision()).isEqualTo(Decision.MATCH);
        assertThat(response.verified()).isTrue();
        assertThat(response.blockchainConfirmed()).isFalse();
        assertThat(response.warning()).contains("unconfirmed");
    }

    @Test
    void ledgerMismatchPreservesHighSimilarity() throws Exception {
        ReflectionTestUtils.setField(service, "requireLiveBlockchain", true);
        candidate(0.97, "0xdeadbeef", 12345L);
        given(blockchain.verifyOnChain("P001", "feature-hash")).willReturn(false);

        var response = service.verify(request);
        assertThat(response.decision()).isEqualTo(Decision.LEDGER_MISMATCH);
        assertThat(response.verified()).isFalse();
        assertThat(response.confidence()).isEqualTo(0.97);
    }

    @Test
    void unavailableLiveLedgerReturnsPersistedAttestation() throws Exception {
        ReflectionTestUtils.setField(service, "requireLiveBlockchain", true);
        candidate(0.97, "0xdeadbeef", 12345L);
        given(blockchain.verifyOnChain("P001", "feature-hash")).willThrow(new IllegalStateException());

        var response = service.verify(request);
        assertThat(response.decision()).isEqualTo(Decision.MATCH);
        assertThat(response.blockchainConfirmed()).isTrue();
        assertThat(response.transactionHash()).isEqualTo("0xdeadbeef");
        assertThat(response.warning()).contains("unavailable");
    }

    private void candidate(double score, String hash, Long block) {
        given(fingerprints.findVerificationCandidate(eq(tenantId), eq("P001"), anyString()))
                .willReturn(List.<Object[]>of(new Object[]{"feature-hash", hash, block, score}));
    }
}
