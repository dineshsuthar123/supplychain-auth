package com.supplychain.verificationservice.repository;

import com.supplychain.verificationservice.entity.VerificationLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface VerificationLogRepository extends MongoRepository<VerificationLog, String> {
    List<VerificationLog> findByProductSerialNumber(String productSerialNumber);
}
