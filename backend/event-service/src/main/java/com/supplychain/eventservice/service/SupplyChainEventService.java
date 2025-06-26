package com.supplychain.eventservice.service;

import com.supplychain.eventservice.dto.SupplyChainEventRequest;
import com.supplychain.eventservice.dto.SupplyChainEventResponse;
import com.supplychain.eventservice.entity.SupplyChainEvent;
import com.supplychain.eventservice.repository.SupplyChainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class SupplyChainEventService {
    private final SupplyChainEventRepository eventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SupplyChainEventResponse createEvent(SupplyChainEventRequest request) {
        SupplyChainEvent event = SupplyChainEvent.builder()
                .eventType(request.getEventType())
                .productSerialNumber(request.getProductSerialNumber())
                .payload(request.getPayload())
                .eventTime(request.getEventTime() != null ? request.getEventTime() : Instant.now())
                .build();
        event = eventRepository.save(event);
        kafkaTemplate.send("supply_chain_events", event.getProductSerialNumber(), event.getPayload());
        SupplyChainEventResponse response = new SupplyChainEventResponse();
        response.setId(event.getId());
        response.setEventType(event.getEventType());
        response.setProductSerialNumber(event.getProductSerialNumber());
        response.setPayload(event.getPayload());
        response.setEventTime(event.getEventTime());
        return response;
    }
}
