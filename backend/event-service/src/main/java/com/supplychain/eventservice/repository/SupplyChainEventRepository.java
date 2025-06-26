package com.supplychain.eventservice.repository;

import com.supplychain.eventservice.entity.SupplyChainEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface SupplyChainEventRepository extends MongoRepository<SupplyChainEvent, String> {
    List<SupplyChainEvent> findByProductSerialNumber(String productSerialNumber);
}
