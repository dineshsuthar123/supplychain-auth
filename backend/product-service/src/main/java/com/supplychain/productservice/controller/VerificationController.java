package com.supplychain.productservice.controller;

import com.supplychain.productservice.dto.ErrorResponse;
import com.supplychain.productservice.dto.VerificationRequest;
import com.supplychain.productservice.dto.VerificationResponse;
import com.supplychain.productservice.service.VerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class VerificationController {
    
    private final VerificationService verificationService;

    @PostMapping
    public ResponseEntity<?> verifyProduct(@Valid @RequestBody VerificationRequest request) {
        log.info("Verification request received for serial: {}", request.getProductSerialNumber());
        try {
            VerificationResponse response = verificationService.verifyProduct(request);
            log.info("Verification completed: verified={}", response.isVerified());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Verification failed: {}", e.getMessage());
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("PRODUCT_NOT_FOUND", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VERIFICATION_ERROR", e.getMessage()));
        }
    }

    @GetMapping("/{serialNumber}")
    public ResponseEntity<?> getVerificationStatus(@PathVariable String serialNumber) {
        log.info("Verification status request for serial: {}", serialNumber);
        try {
            VerificationRequest request = new VerificationRequest();
            request.setProductSerialNumber(serialNumber);
            VerificationResponse response = verificationService.verifyProduct(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Verification status check failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PRODUCT_NOT_FOUND", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Verification service is healthy");
    }
}
