package com.supplychain.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for product fingerprint enrollment.
 *
 * The caller supplies the 128-dimensional embedding produced by the ONNX model
 * (either on-device in the mobile app or via the server-side inference endpoint).
 */
public record EnrollRequest(

        @NotBlank(message = "productId must not be blank")
        String productId,

        /** Optional JSON metadata (manufacturer, batch, SKU, etc.). */
        String metadata,

        @NotNull(message = "embedding must not be null")
        @Size(min = 128, max = 128, message = "embedding must have exactly 128 dimensions")
        List<Double> embedding
) {}
