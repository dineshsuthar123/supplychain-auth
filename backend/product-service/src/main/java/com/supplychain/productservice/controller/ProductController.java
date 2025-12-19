package com.supplychain.productservice.controller;

import com.supplychain.productservice.dto.ErrorResponse;
import com.supplychain.productservice.dto.ProductRegistrationRequest;
import com.supplychain.productservice.dto.ProductResponse;
import com.supplychain.productservice.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;
    
    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<?> registerProduct(@Valid @RequestBody ProductRegistrationRequest request) {
        try {
            ProductResponse response = productService.registerProduct(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("already exists")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("DUPLICATE_SERIAL_NUMBER", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("REGISTRATION_ERROR", e.getMessage()));
        }
    }
    
    // Ultra-fast registration - minimal response for high throughput
    @PostMapping("/fast")
    public ResponseEntity<String> registerProductFast(@Valid @RequestBody ProductRegistrationRequest request) {
        String result = productService.registerProductFast(request);
        if (result.equals("EXISTS")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("EXISTS");
        }
        return ResponseEntity.ok(result);
    }
    
    // Batch registration - up to 100 products at once
    @PostMapping("/batch")
    public ResponseEntity<Map<String, String>> registerBatch(@RequestBody List<ProductRegistrationRequest> requests) {
        if (requests.size() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Max 100 products per batch"));
        }
        
        Map<String, String> results = requests.parallelStream()
            .collect(Collectors.toMap(
                ProductRegistrationRequest::getSerialNumber,
                req -> productService.registerProductFast(req)
            ));
        
        return ResponseEntity.ok(results);
    }
    
    // Fast existence check
    @GetMapping("/exists/{serialNumber}")
    public ResponseEntity<Boolean> productExists(@PathVariable String serialNumber) {
        return ResponseEntity.ok(productService.productExists(serialNumber));
    }

    @GetMapping("/{serialNumber}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable String serialNumber) {
        ProductResponse response = productService.getProductBySerial(serialNumber);
        return ResponseEntity.ok(response);
    }
}
