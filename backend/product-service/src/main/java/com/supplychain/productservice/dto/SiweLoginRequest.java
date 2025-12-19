package com.supplychain.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SiweLoginRequest {
    @NotBlank(message = "Wallet address is required")
    private String walletAddress;

    @NotBlank(message = "Signature is required")
    private String signature;

    @NotBlank(message = "Message is required")
    private String message;

    @NotBlank(message = "Nonce is required")
    private String nonce;
}
