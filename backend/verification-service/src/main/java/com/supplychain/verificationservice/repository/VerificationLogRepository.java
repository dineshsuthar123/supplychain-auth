package com.supplychain.verificationservice.repository;

import com.supplychain.verificationservice.entity.VerificationLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface VerificationLogRepository extends MongoRepository<VerificationLog, String> {
    List<VerificationLog> findByProductSerialNumber(String productSerialNumber);
    Optional<VerificationLog> findFirstByProductSerialNumberOrderByVerifiedAtDesc(String productSerialNumber);
}
