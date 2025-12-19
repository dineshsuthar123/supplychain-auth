package com.supplychain.productservice.service;

import com.supplychain.productservice.dto.*;
import com.supplychain.productservice.entity.User;
import com.supplychain.productservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // In-memory nonce store (in production, use Redis)
    private final Map<String, Instant> nonceStore = new ConcurrentHashMap<>();

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request.getEmail());

        // Validate unique constraints
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (request.getWalletAddress() != null && userRepository.existsByWalletAddress(request.getWalletAddress())) {
            throw new RuntimeException("Wallet address already linked to another account");
        }

        // Validate role
        String role = request.getRole() != null ? request.getRole().toUpperCase() : "USER";
        if (!role.matches("USER|MANUFACTURER|VERIFIER")) {
            role = "USER";
        }

        // Create user
        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : request.getUsername())
                .company(request.getCompany())
                .role(role)
                .walletAddress(request.getWalletAddress())
                .enabled(true)
                .emailVerified(false)
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully: id={}", user.getId());

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for: {}", request.getEmailOrUsername());

        User user = userRepository.findByEmailOrUsername(request.getEmailOrUsername(), request.getEmailOrUsername())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Invalid password for user: {}", user.getEmail());
            throw new RuntimeException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new RuntimeException("Account is disabled");
        }

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("Login successful for user: id={}", user.getId());
        return generateAuthResponse(user);
    }

    public String generateNonce(String walletAddress) {
        String nonce = UUID.randomUUID().toString();
        nonceStore.put(walletAddress.toLowerCase() + ":" + nonce, Instant.now().plusSeconds(300)); // 5 min expiry
        return nonce;
    }

    @Transactional
    public AuthResponse loginWithWallet(SiweLoginRequest request) {
        log.info("SIWE login attempt for wallet: {}", request.getWalletAddress());

        String nonceKey = request.getWalletAddress().toLowerCase() + ":" + request.getNonce();
        Instant nonceExpiry = nonceStore.get(nonceKey);

        if (nonceExpiry == null || nonceExpiry.isBefore(Instant.now())) {
            throw new RuntimeException("Invalid or expired nonce");
        }

        // Remove used nonce (prevent replay)
        nonceStore.remove(nonceKey);

        // Verify signature (simplified - in production use web3j or SIWE library)
        // For now, we trust the client-side verification
        // TODO: Implement proper SIWE signature verification

        // Find or create user by wallet address
        User user = userRepository.findByWalletAddress(request.getWalletAddress())
                .orElseGet(() -> {
                    // Create new user for this wallet
                    User newUser = User.builder()
                            .email(request.getWalletAddress().toLowerCase() + "@wallet.local")
                            .username("wallet_" + request.getWalletAddress().substring(2, 10).toLowerCase())
                            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .displayName("Wallet " + request.getWalletAddress().substring(0, 10))
                            .role("USER")
                            .walletAddress(request.getWalletAddress())
                            .enabled(true)
                            .emailVerified(false)
                            .build();
                    return userRepository.save(newUser);
                });

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("SIWE login successful for user: id={}", user.getId());
        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Refreshing token");

        if (!jwtService.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        if (!"refresh".equals(jwtService.getTokenType(refreshToken))) {
            throw new RuntimeException("Invalid token type");
        }

        Long userId = jwtService.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isEnabled()) {
            throw new RuntimeException("Account is disabled");
        }

        // Generate new tokens (rotation)
        return generateAuthResponse(user);
    }

    @Transactional
    public void logout(Long userId) {
        log.info("Logging out user: {}", userId);
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setRefreshTokenHash(null);
            user.setRefreshTokenExpiresAt(null);
            userRepository.save(user);
        }
    }

    public Optional<User> getCurrentUser(String token) {
        try {
            Long userId = jwtService.getUserIdFromToken(token);
            return userRepository.findById(userId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        // Store hashed refresh token
        user.setRefreshTokenHash(passwordEncoder.encode(refreshToken));
        user.setRefreshTokenExpiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()));
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationSeconds())
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName())
                        .company(user.getCompany())
                        .role(user.getRole())
                        .walletAddress(user.getWalletAddress())
                        .emailVerified(user.isEmailVerified())
                        .build())
                .build();
    }
}
