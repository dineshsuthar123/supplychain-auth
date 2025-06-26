package com.supplychain.eventservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "supply_chain_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplyChainEvent {
    @Id
    private String id;
    private String eventType;
    private String productSerialNumber;
    private String payload;
    private Instant eventTime;
}
