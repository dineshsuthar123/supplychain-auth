package com.supplychain.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String tokenType;
    private long expiresIn;  // seconds
    private UserDto user;
    @JsonIgnore
    private String refreshToken;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private UUID id;
        private UUID tenantId;
        private String email;
        private String username;
        private String displayName;
        private String company;
        private String role;
        private String walletAddress;
        private boolean emailVerified;
    }
}
