package com.supplychain.productservice.realworld;

import com.supplychain.productservice.dto.ProductRegistrationRequest;
import com.supplychain.productservice.dto.ProductResponse;
import com.supplychain.productservice.entity.Product;
import com.supplychain.productservice.repository.ProductRepository;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resilient product registration with retry.
 *
 * Redis and Kafka dependencies have been removed; cache warm-up is no-op
 * and audit events are logged only. The DB write remains fully transactional.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientProductService {

    private final ProductRepository productRepository;
    private final RealWorldCacheService cacheService;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * Register product with retry on transient DB failures.
     */
    @Transactional
    @Retry(name = "db-write", fallbackMethod = "registerFailed")
    public ProductResponse registerProduct(ProductRegistrationRequest request) {
        if (productRepository.existsBySerialNumber(request.getSerialNumber())) {
            throw new ProductAlreadyExistsException(request.getSerialNumber());
        }

        Product product = Product.builder()
                .serialNumber(request.getSerialNumber())
                .name(request.getName())
                .manufacturer(request.getManufacturer())
                .metadataUri(request.getMetadataUri())
                .registeredAt(Instant.now())
                .nftTokenId(generateTokenId())
                .build();

        product = productRepository.save(product);

        // Warm in-memory cache
        cacheService.onProductRegistered(product.getSerialNumber());

        // Fire-and-forget audit log
        publishRegistrationEventAsync(product);

        return toResponse(product);
    }

    @Async
    public void publishRegistrationEventAsync(Product product) {
        log.debug("audit registration serial={} name={}", product.getSerialNumber(), product.getName());
    }

    public ProductResponse registerFailed(ProductRegistrationRequest request, Throwable t) {
        log.error("Product registration failed after retries for {}: {}",
                request.getSerialNumber(), t.getMessage());
        throw new RuntimeException("Registration failed after retries: " + t.getMessage(), t);
    }

    private ProductResponse toResponse(Product product) {
        ProductResponse r = new ProductResponse();
        r.setId(product.getId());
        r.setSerialNumber(product.getSerialNumber());
        r.setName(product.getName());
        r.setManufacturer(product.getManufacturer());
        r.setMetadataUri(product.getMetadataUri());
        r.setRegisteredAt(product.getRegisteredAt());
        r.setNftTokenId(product.getNftTokenId());
        return r;
    }

    private String generateTokenId() {
        StringBuilder sb = new StringBuilder(20);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) sb.append(HEX[rng.nextInt(16)]);
        return sb.toString();
    }

    /** Typed exception for 409 Conflict handling. */
    public static class ProductAlreadyExistsException extends RuntimeException {
        public ProductAlreadyExistsException(String serial) {
            super("Product with serial '" + serial + "' already exists");
        }
    }
}
