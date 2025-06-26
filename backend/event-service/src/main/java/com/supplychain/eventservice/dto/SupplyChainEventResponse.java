package com.supplychain.eventservice.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class SupplyChainEventResponse {
    private String id;
    private String eventType;
    private String productSerialNumber;
    private String payload;
    private Instant eventTime;
}
