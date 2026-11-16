package com.supplychain.productservice.service;
import com.supplychain.productservice.blockchain.BlockchainService;
import com.supplychain.productservice.dto.VerifyRequest;
import com.supplychain.productservice.repository.*;
import com.supplychain.productservice.tenant.TenantContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class FingerprintVerificationServiceTest {
 @Mock ProductFingerprintRepository fingerprints;
 @Mock VerificationEventRepository events;
 @Mock BlockchainService blockchain;
 final UUID tenant=UUID.randomUUID();
 FingerprintVerificationService service;
 @BeforeEach void setup(){TenantContext.set(tenant);service=new FingerprintVerificationService(fingerprints,events,blockchain,new SimpleMeterRegistry());ReflectionTestUtils.setField(service,"similarityThreshold",0.85);}
 @AfterEach void clear(){TenantContext.clear();}
 @Test void unknownIsTenantScopedAndAudited(){when(fingerprints.findVerificationCandidate(eq(tenant),eq("unknown"),anyString())).thenReturn(List.of());assertThat(service.verify(request("unknown")).verified()).isFalse();verify(events).save(argThat(e->e.getTenantId().equals(tenant)&&!e.isVerified()));}
 @Test void belowThresholdIsRejected(){candidate(0.6,null,null);assertThat(service.verify(request("P001")).verified()).isFalse();verifyNoInteractions(blockchain);}
 @Test void persistedAttestationDoesNotRequireRpc(){candidate(0.97,"0xdeadbeef",12345L);var r=service.verify(request("P001"));assertThat(r.verified()).isTrue();assertThat(r.blockchainConfirmed()).isTrue();assertThat(r.transactionHash()).isEqualTo("0xdeadbeef");verifyNoInteractions(blockchain);}
 @Test void missingAttestationIsExplicit(){candidate(0.95,null,null);var r=service.verify(request("P001"));assertThat(r.verified()).isTrue();assertThat(r.blockchainConfirmed()).isFalse();assertThat(r.warning()).contains("pending");verifyNoInteractions(blockchain);}
 void candidate(double similarity,String transaction,Long block){when(fingerprints.findVerificationCandidate(eq(tenant),eq("P001"),anyString())).thenReturn(Collections.singletonList(new Object[]{"hash",transaction,block,similarity}));}
 VerifyRequest request(String p){return new VerifyRequest(p,Collections.nCopies(128,0.1));}
}
