package com.supplychain.productservice.service;

import com.supplychain.productservice.dto.DashboardResponse;
import com.supplychain.productservice.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tenant-scoped, read-optimised database view for the operations dashboard. */
@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    private final Timer queryTimer;
    public DashboardService(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.queryTimer = Timer.builder("dashboard.query.duration").register(registry);
    }
    public DashboardResponse snapshot() {
        return queryTimer.record(() -> snapshot(TenantContext.getRequired()));
    }
    private DashboardResponse snapshot(UUID tenantId) {
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT
                  (SELECT count(*) FROM product_fingerprints WHERE tenant_id = ?) AS products_attested,
                  (SELECT count(*) FROM product_fingerprints WHERE tenant_id = ? AND created_at >= date_trunc('day', now())) AS products_today,
                  (SELECT count(*) FROM verification_events WHERE tenant_id = ? AND created_at >= date_trunc('day', now())) AS verifications_today,
                  (SELECT count(*) FROM verification_events WHERE tenant_id = ? AND created_at >= date_trunc('day', now()) AND verified = false) AS failed_today,
                  (SELECT count(*) FROM blockchain_outbox WHERE tenant_id = ? AND status = 'PENDING') AS pending_attestations
                """, tenantId, tenantId, tenantId, tenantId, tenantId);
        List<DashboardResponse.Activity> activity = jdbc.query("""
                SELECT action, product_id, created_at, state FROM (
                  SELECT 'Product attested' AS action, product_id, created_at, 'ATTESTED' AS state FROM product_fingerprints WHERE tenant_id = ?
                  UNION ALL
                  SELECT CASE WHEN verified THEN 'Verification passed' ELSE 'Identity mismatch' END,
                         product_id, created_at, CASE WHEN verified THEN 'CLEARED' ELSE 'REVIEW' END FROM verification_events WHERE tenant_id = ?
                ) events ORDER BY created_at DESC LIMIT 10
                """, (rs, rowNum) -> new DashboardResponse.Activity(rs.getString("action"), rs.getString("product_id"),
                rs.getTimestamp("created_at").toLocalDateTime().toString(), rs.getString("state")), tenantId, tenantId);
        return new DashboardResponse(number(totals, "products_attested"), number(totals, "products_today"), number(totals, "verifications_today"), number(totals, "failed_today"), number(totals, "pending_attestations"), activity);
    }
    private static long number(Map<String, Object> values, String key) { return ((Number) values.get(key)).longValue(); }
}
