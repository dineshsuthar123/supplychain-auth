package com.supplychain.verificationservice.controller;

import com.supplychain.verificationservice.dto.VerificationRequest;
import com.supplychain.verificationservice.dto.VerificationResponse;
import com.supplychain.verificationservice.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class VerificationController {
    private final VerificationService verificationService;

    @PostMapping
    public ResponseEntity<VerificationResponse> verifyProduct(@RequestBody VerificationRequest request) {
        VerificationResponse response = verificationService.verifyProduct(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{serialNumber}")
    public ResponseEntity<VerificationResponse> getVerification(@PathVariable String serialNumber) {
        VerificationRequest request = new VerificationRequest();
        request.setProductSerialNumber(serialNumber); // Lombok @Data provides setter
        VerificationResponse response = verificationService.verifyProduct(request);
        return ResponseEntity.ok(response);
    }
}
