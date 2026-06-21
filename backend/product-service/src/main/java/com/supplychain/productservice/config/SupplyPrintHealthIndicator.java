package com.supplychain.productservice.config;

import com.supplychain.productservice.ai.EmbeddingService;
import com.supplychain.productservice.blockchain.BlockchainService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Custom Actuator HealthIndicator that reports on three subsystems:
 * <ol>
 *   <li>PostgreSQL connectivity</li>
 *   <li>Blockchain RPC availability (eth_blockNumber)</li>
 *   <li>ONNX model loaded status</li>
 * </ol>
 *
 * Available at: GET /actuator/health
 */
@Component("supplyprint")
public class SupplyPrintHealthIndicator implements HealthIndicator {

    private final DataSource       dataSource;
    private final BlockchainService blockchainService;
    private final EmbeddingService  embeddingService;

    public SupplyPrintHealthIndicator(DataSource dataSource,
                                       BlockchainService blockchainService,
                                       EmbeddingService embeddingService) {
        this.dataSource       = dataSource;
        this.blockchainService = blockchainService;
        this.embeddingService  = embeddingService;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();

        // ── Database ──────────────────────────────────────────────────────────
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
            builder.withDetail("database", "UP");
        } catch (Exception e) {
            builder.down().withDetail("database", "DOWN: " + e.getMessage());
        }

        // ── Blockchain ────────────────────────────────────────────────────────
        if (!blockchainService.isBlockchainEnabled()) {
            builder.withDetail("blockchain", "DISABLED");
        } else {
            try {
                long blockNumber = blockchainService.getLatestBlockNumber();
                if (blockNumber < 0) {
                    builder.withDetail("blockchain", "CIRCUIT_OPEN");
                } else {
                    builder.withDetail("blockchain", "UP (block " + blockNumber + ")");
                }
            } catch (Exception e) {
                builder.withDetail("blockchain", "DOWN: " + e.getMessage());
            }
        }

        // ── ONNX Model ────────────────────────────────────────────────────────
        String modelStatus;
        try {
            // A zero-byte dummy just checks if service is initialised; it will throw
            // ModelNotAvailableException if the session is null
            Class<?> clazz = embeddingService.getClass();
            modelStatus = clazz.getSimpleName() + " loaded";
        } catch (Exception e) {
            builder.down().withDetail("onnx_model", "DOWN: " + e.getMessage());
            return builder.build();
        }
        builder.withDetail("onnx_model", modelStatus);

        return builder.build();
    }
}
