package com.supplychain.productservice.service;

import com.supplychain.productservice.dto.ProductRegistrationRequest;
import com.supplychain.productservice.dto.ProductResponse;
import com.supplychain.productservice.entity.Product;
import com.supplychain.productservice.repository.ProductRepository;
import com.supplychain.productservice.realworld.RealWorldCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.crypto.Credentials;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final Web3j web3j;
    private final Credentials credentials;
    private final RealWorldCacheService cacheService;
    
    // Pre-allocated hex chars for fast token generation
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    @Autowired
    public ProductService(ProductRepository productRepository,
                          @Autowired(required = false) Web3j web3j,
                          @Autowired(required = false) Credentials credentials,
                          @Autowired(required = false) RealWorldCacheService cacheService) {
        this.productRepository = productRepository;
        this.web3j = web3j;
        this.credentials = credentials;
        this.cacheService = cacheService;
    }

    @Transactional
    @CacheEvict(value = {"products", "verifications", "fastVerifications"}, key = "#request.serialNumber")
    public ProductResponse registerProduct(ProductRegistrationRequest request) {
        // Fast existence check - O(1) without loading entity
        if (productRepository.existsBySerialNumber(request.getSerialNumber())) {
            throw new RuntimeException("Product with serial number '" + request.getSerialNumber() + "' already exists");
        }
        
        // Build product with fast token ID generation
        Product product = Product.builder()
                .serialNumber(request.getSerialNumber())
                .name(request.getName())
                .manufacturer(request.getManufacturer())
                .metadataUri(request.getMetadataUri())
                .registeredAt(Instant.now())
                .nftTokenId(generateFastTokenId())
                .build();

        product = productRepository.save(product);

        // Eagerly populate Redis cache so the FIRST verification of this serial is a cache hit
        if (cacheService != null) {
            cacheService.onProductRegistered(product.getSerialNumber());
        }

        // Direct field mapping - no reflection
        return buildResponse(product);
    }
    
    // Ultra-fast registration - minimal response for high throughput
    @Transactional
    public String registerProductFast(ProductRegistrationRequest request) {
        if (productRepository.existsBySerialNumber(request.getSerialNumber())) {
            return "EXISTS";
        }
        
        Product product = Product.builder()
                .serialNumber(request.getSerialNumber())
                .name(request.getName())
                .manufacturer(request.getManufacturer())
                .metadataUri(request.getMetadataUri())
                .registeredAt(Instant.now())
                .nftTokenId(generateFastTokenId())
                .build();

        productRepository.save(product);
        return "OK:" + product.getSerialNumber();
    }

    @Cacheable(value = "products", key = "#serialNumber")
    public ProductResponse getProductBySerial(String serialNumber) {
        Product product = productRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return buildResponse(product);
    }
    
    // Fast existence check - cached
    @Cacheable(value = "products", key = "'exists:' + #serialNumber")
    public boolean productExists(String serialNumber) {
        return productRepository.existsBySerialNumber(serialNumber);
    }
    
    // Helper: Build response without reflection overhead
    private ProductResponse buildResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSerialNumber(product.getSerialNumber());
        response.setName(product.getName());
        response.setManufacturer(product.getManufacturer());
        response.setMetadataUri(product.getMetadataUri());
        response.setRegisteredAt(product.getRegisteredAt());
        response.setNftTokenId(product.getNftTokenId());
        return response;
    }
    
    // Ultra-fast token ID generation - no UUID overhead
    private String generateFastTokenId() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        char[] buf = new char[16];
        for (int i = 0; i < 16; i++) {
            buf[i] = HEX[rand.nextInt(16)];
        }
        return new String(buf);
    }
}
