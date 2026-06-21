package com.supplychain.productservice.controller;

import com.supplychain.productservice.dto.EnrollRequest;
import com.supplychain.productservice.dto.EnrollResponse;
import com.supplychain.productservice.dto.VerifyRequest;
import com.supplychain.productservice.dto.VerifyResponse;
import com.supplychain.productservice.ai.EmbeddingService;
import com.supplychain.productservice.entity.ProductFingerprint;
import com.supplychain.productservice.exception.DuplicateProductException;
import com.supplychain.productservice.repository.ProductFingerprintRepository;
import com.supplychain.productservice.service.FingerprintEnrollmentService;
import com.supplychain.productservice.service.FingerprintVerificationService;
import com.supplychain.productservice.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import com.supplychain.productservice.tenant.TenantContext;
import com.supplychain.productservice.security.ImageCaptureValidator;

/**
 * REST API for SupplyPrint fingerprint enrollment and verification.
 *
 * <pre>
 * POST /api/enroll          – enroll a new product fingerprint
 * POST /api/verify          – verify a product scan against the DB + blockchain
 * GET  /api/verify/{id}/log – get enrollment status / on-chain details
 * </pre>
 */
@RestController
@RequestMapping("/api")
@Tag(name = "SupplyPrint", description = "Physical fingerprint anti-counterfeit API")
public class SupplyPrintController {

    private final FingerprintEnrollmentService  enrollmentService;
    private final FingerprintVerificationService verificationService;
    private final ProductFingerprintRepository  fingerprintRepo;
    private final EmbeddingService embeddingService;
    private final DashboardService dashboardService;

    public SupplyPrintController(FingerprintEnrollmentService enrollmentService,
                                  FingerprintVerificationService verificationService,
                                  ProductFingerprintRepository fingerprintRepo,
                                  EmbeddingService embeddingService,
                                  DashboardService dashboardService) {
        this.enrollmentService  = enrollmentService;
        this.verificationService = verificationService;
        this.fingerprintRepo    = fingerprintRepo;
        this.embeddingService   = embeddingService;
        this.dashboardService   = dashboardService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get database-derived workspace telemetry")
    public ResponseEntity<?> dashboard() {
        return ResponseEntity.ok(dashboardService.snapshot());
    }

    /**
     * Production capture endpoint. The server derives the fingerprint directly
     * from the supplied physical-product image using the configured ONNX model.
     * No browser-generated or synthetic embeddings are accepted by this flow.
     */
    @PostMapping(value = "/enroll/image", consumes = "multipart/form-data")
    @Operation(summary = "Enroll from a physical-product image")
    public ResponseEntity<?> enrollImage(@RequestParam String productId,
                                         @RequestParam(required = false) String metadata,
                                         @RequestParam("image") MultipartFile image) {
        try {
            EnrollResponse response = enrollmentService.enroll(new EnrollRequest(
                    productId, metadata, embeddingFrom(image)));
            return ResponseEntity.accepted().body(response);
        } catch (DuplicateProductException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage(), "productId", ex.getProductId()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Enroll a product fingerprint.
     *
     * <p>Returns HTTP 202 Accepted immediately; blockchain write is async.
     * Returns HTTP 409 Conflict if productId already exists.
     */
    @PostMapping("/enroll")
    @Operation(summary = "Enroll a product fingerprint",
               description = "Stores the 128-dim embedding and queues an on-chain hash write.")
    public ResponseEntity<?> enroll(@Valid @RequestBody EnrollRequest request) {
        try {
            EnrollResponse response = enrollmentService.enroll(request);
            return ResponseEntity.accepted().body(response);
        } catch (DuplicateProductException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage(), "productId", ex.getProductId()));
        }
    }

    /**
     * Verify a product scan.
     *
     * <p>Returns HTTP 200 with {@code verified: true/false}.
     * The HTTP status is always 200 (the verification result is in the body).
     */
    @PostMapping("/verify")
    @Operation(summary = "Verify a product fingerprint scan",
               description = "Matches the query embedding against the database and confirms on-chain.")
    public ResponseEntity<VerifyResponse> verify(@Valid @RequestBody VerifyRequest request) {
        VerifyResponse response = verificationService.verify(request);
        return ResponseEntity.ok(response);
    }

    /** Verifies a fresh physical capture through server-side ONNX inference. */
    @PostMapping(value = "/verify/image", consumes = "multipart/form-data")
    @Operation(summary = "Verify from a physical-product image")
    public ResponseEntity<?> verifyImage(@RequestParam String productId,
                                         @RequestParam("image") MultipartFile image) {
        try {
            return ResponseEntity.ok(verificationService.verify(new VerifyRequest(productId, embeddingFrom(image))));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Returns the enrollment record + on-chain status for a given productId.
     * Useful as an immutable audit trail link (can be bookmarked alongside Polygonscan).
     */
    @GetMapping("/verify/{productId}/log")
    @Operation(summary = "Get enrollment log for a product",
               description = "Returns the stored featureHash, txHash, and blockNumber.")
    public ResponseEntity<?> enrollmentLog(@PathVariable String productId) {
        List<Object[]> rows = fingerprintRepo.findEnrollmentLog(TenantContext.getRequired(), productId);
        if (rows.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Product not found: " + productId));
        }
        Object[] fp = rows.get(0);
        return ResponseEntity.ok(Map.of(
                "productId",        fp[0],
                "featureHash",      fp[1],
                "transactionHash",  fp[2] != null ? fp[2] : "PENDING",
                "blockNumber",      fp[3] != null ? fp[3] : -1,
                "createdAt",        fp[4],
                "polygonscanUrl",   fp[2] != null
                        ? "https://mumbai.polygonscan.com/tx/" + fp[2]
                        : "Pending blockchain confirmation"
        ));
    }

    private java.util.List<Double> embeddingFrom(MultipartFile image) throws java.io.IOException {
        if (image.isEmpty()) throw new IllegalArgumentException("An image capture is required");
        byte[] capture = image.getBytes();
        ImageCaptureValidator.validate(capture);
        float[] embedding = embeddingService.getEmbedding(capture);
        var values = new ArrayList<Double>(embedding.length);
        for (float value : embedding) values.add((double) value);
        return values;
    }
}
