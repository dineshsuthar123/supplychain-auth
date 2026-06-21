package com.supplychain.productservice.dto;

import java.util.List;

/** Database-derived workspace telemetry; no client-side sample figures. */
public record DashboardResponse(
        long productsAttested,
        long productsAttestedToday,
        long verificationsToday,
        long failedVerificationsToday,
        long pendingAttestations,
        List<Activity> recentActivity
) {
    public record Activity(String action, String productId, String time, String state) { }
}
