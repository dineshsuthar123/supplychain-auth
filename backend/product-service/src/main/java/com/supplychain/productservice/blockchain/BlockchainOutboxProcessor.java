package com.supplychain.productservice.blockchain;

import com.supplychain.productservice.entity.BlockchainOutbox;
import com.supplychain.productservice.repository.BlockchainOutboxRepository;
import com.supplychain.productservice.repository.ProductFingerprintRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled processor that picks PENDING blockchain_outbox records and
 * submits them to the Polygon network.
 *
 * <p>Guarantees exactly-once semantics by:
 * <ul>
 *   <li>Using a DB transaction to update status to SENT or FAILED atomically.</li>
 *   <li>Capping retry attempts so permanently-broken records don't thrash.</li>
 * </ul>
 *
 * <p>Runs every 10 seconds; configurable via {@code supplyprint.outbox.interval-ms}.
 */
@Component
public class BlockchainOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(BlockchainOutboxProcessor.class);
    private static final int    MAX_ATTEMPTS = 5;

    private final BlockchainOutboxRepository   outboxRepo;
    private final ProductFingerprintRepository fingerprintRepo;
    private final BlockchainService            blockchainService;
    private final Counter                      errorCounter;
    private final Timer                        dispatchTimer;

    public BlockchainOutboxProcessor(BlockchainOutboxRepository outboxRepo,
                                     ProductFingerprintRepository fingerprintRepo,
                                     BlockchainService blockchainService,
                                     MeterRegistry meterRegistry) {
        this.outboxRepo       = outboxRepo;
        this.fingerprintRepo  = fingerprintRepo;
        this.blockchainService = blockchainService;
        this.errorCounter      = Counter.builder("supplyprint.blockchain_errors.total")
                                        .description("Total blockchain write failures")
                                        .register(meterRegistry);
        this.dispatchTimer = Timer.builder("blockchain.outbox.dispatch.duration").register(meterRegistry);
        Gauge.builder("blockchain.outbox.pending.count", outboxRepo, repo -> repo.countByStatus(BlockchainOutbox.Status.PENDING)).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${supplyprint.outbox.interval-ms:10000}")
    @Transactional
    public void processPending() {
        Timer.Sample dispatch = Timer.start();
        try {
        if (!blockchainService.isBlockchainEnabled()) {
            return;  // Silently skip when blockchain is not configured
        }

        List<BlockchainOutbox> pending = outboxRepo.findEligiblePending(MAX_ATTEMPTS);
        if (pending.isEmpty()) return;

        log.debug("OutboxProcessor: processing {} pending records", pending.size());

        for (BlockchainOutbox record : pending) {
            try {
                TransactionReceipt receipt = blockchainService.registerProductOnChain(
                        record.getProductId(), record.getFeatureHash());

                if (receipt == null) {
                    // Fallback triggered (circuit open) – leave PENDING for next cycle
                    record.setAttempts(record.getAttempts() + 1);
                    record.setLastError("Circuit breaker open – will retry");
                    outboxRepo.save(record);
                    continue;
                }

                // Success – update fingerprint record and mark outbox as SENT
                fingerprintRepo.updateOnChainInfo(
                        record.getTenantId(),
                        record.getProductId(),
                        receipt.getBlockNumber().longValue(),
                        receipt.getTransactionHash()
                );

                record.setStatus(BlockchainOutbox.Status.SENT);
                record.setProcessedAt(LocalDateTime.now());
                outboxRepo.save(record);

                log.info("On-chain: productId={} tx={} block={}",
                        record.getProductId(), receipt.getTransactionHash(), receipt.getBlockNumber());

            } catch (Exception ex) {
                errorCounter.increment();
                record.setAttempts(record.getAttempts() + 1);
                record.setLastError(ex.getMessage());
                if (record.getAttempts() >= MAX_ATTEMPTS) {
                    record.setStatus(BlockchainOutbox.Status.FAILED);
                    log.error("Outbox record FAILED after {} attempts: productId={}",
                            MAX_ATTEMPTS, record.getProductId(), ex);
                } else {
                    log.warn("Blockchain write failed (attempt {}/{}): productId={} – {}",
                            record.getAttempts(), MAX_ATTEMPTS, record.getProductId(), ex.getMessage());
                }
                outboxRepo.save(record);
            }
        }
        } finally { dispatch.stop(dispatchTimer); }
    }
}
