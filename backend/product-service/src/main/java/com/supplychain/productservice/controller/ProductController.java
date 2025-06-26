package com.supplychain.productservice.controller;

import com.supplychain.productservice.dto.ErrorResponse;
import com.supplychain.productservice.dto.ProductRegistrationRequest;
import com.supplychain.productservice.dto.ProductResponse;
import com.supplychain.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

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

    @GetMapping("/{serialNumber}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable String serialNumber) {
        ProductResponse response = productService.getProductBySerial(serialNumber);
        return ResponseEntity.ok(response);
    }
}
