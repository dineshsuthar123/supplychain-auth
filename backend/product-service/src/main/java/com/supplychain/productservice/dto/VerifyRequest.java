package com.supplychain.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for verifying a physical product label scan.
 */
public record VerifyRequest(

        @NotBlank(message = "productId must not be blank")
        String productId,

        @NotNull(message = "embedding must not be null")
        @Size(min = 128, max = 128, message = "embedding must have exactly 128 dimensions")
        List<Double> embedding
) {}
