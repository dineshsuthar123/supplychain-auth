package com.supplychain.eventservice.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class SupplyChainEventRequest {
    private String eventType;
    private String productSerialNumber;
    private String payload;
    private Instant eventTime;
}
