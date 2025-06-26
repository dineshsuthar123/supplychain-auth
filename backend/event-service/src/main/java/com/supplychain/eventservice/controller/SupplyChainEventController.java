package com.supplychain.eventservice.controller;

import com.supplychain.eventservice.dto.SupplyChainEventRequest;
import com.supplychain.eventservice.dto.SupplyChainEventResponse;
import com.supplychain.eventservice.service.SupplyChainEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class SupplyChainEventController {
    private final SupplyChainEventService eventService;

    @PostMapping
    public ResponseEntity<SupplyChainEventResponse> createEvent(@RequestBody SupplyChainEventRequest request) {
        SupplyChainEventResponse response = eventService.createEvent(request);
        return ResponseEntity.ok(response);
    }
}
