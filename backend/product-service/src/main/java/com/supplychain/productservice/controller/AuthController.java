package com.supplychain.productservice.controller;

import com.supplychain.productservice.dto.*;
import com.supplychain.productservice.entity.User;
import com.supplychain.productservice.service.AuthService;
import com.supplychain.productservice.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request received for: {}", request.getEmail());
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            log.error("Registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("REGISTRATION_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("Login request received for: {}", request.getEmailOrUsername());
        try {
            AuthResponse authResponse = authService.login(request);
            
            // Set refresh token in HttpOnly cookie
            addRefreshTokenCookie(response, authResponse.getAccessToken());
            
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            log.error("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("LOGIN_ERROR", e.getMessage()));
        }
    }

    @GetMapping("/siwe/nonce")
    public ResponseEntity<?> getSiweNonce(@RequestParam String walletAddress) {
        log.info("SIWE nonce request for wallet: {}", walletAddress);
        String nonce = authService.generateNonce(walletAddress);
        return ResponseEntity.ok(Map.of(
                "nonce", nonce,
                "domain", "supplychain-auth.onrender.com",
                "statement", "Sign in to SupplyChain Auth Platform"
        ));
    }

    @PostMapping("/siwe/login")
    public ResponseEntity<?> siweLogin(@Valid @RequestBody SiweLoginRequest request, HttpServletResponse response) {
        log.info("SIWE login request for wallet: {}", request.getWalletAddress());
        try {
            AuthResponse authResponse = authService.loginWithWallet(request);
            addRefreshTokenCookie(response, authResponse.getAccessToken());
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            log.error("SIWE login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("SIWE_LOGIN_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        log.info("Token refresh request");
        try {
            String refreshToken = extractRefreshToken(request);
            if (refreshToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("REFRESH_ERROR", "No refresh token provided"));
            }
            
            AuthResponse authResponse = authService.refreshToken(refreshToken);
            addRefreshTokenCookie(response, authResponse.getAccessToken());
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            log.error("Token refresh failed: {}", e.getMessage());
            clearRefreshTokenCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("REFRESH_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        log.info("Logout request");
        try {
            String token = extractBearerToken(request);
            if (token != null) {
                Long userId = jwtService.getUserIdFromToken(token);
                authService.logout(userId);
            }
            clearRefreshTokenCookie(response);
            return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
        } catch (Exception e) {
            clearRefreshTokenCookie(response);
            return ResponseEntity.ok(Map.of("message", "Logged out"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("AUTH_ERROR", "No token provided"));
        }

        try {
            Optional<User> userOpt = authService.getCurrentUser(token);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("AUTH_ERROR", "User not found"));
            }

            User user = userOpt.get();
            return ResponseEntity.ok(AuthResponse.UserDto.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .username(user.getUsername())
                    .displayName(user.getDisplayName())
                    .company(user.getCompany())
                    .role(user.getRole())
                    .walletAddress(user.getWalletAddress())
                    .emailVerified(user.isEmailVerified())
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("AUTH_ERROR", "Invalid token"));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "healthy", "service", "auth"));
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("refresh", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(14 * 24 * 60 * 60); // 14 days
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refresh", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refresh".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        // Fallback to header
        String header = request.getHeader("X-Refresh-Token");
        if (header != null) {
            return header;
        }
        return null;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
