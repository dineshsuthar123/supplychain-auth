package com.supplychain.productservice.service;

import com.supplychain.productservice.dto.ProductRegistrationRequest;
import com.supplychain.productservice.dto.ProductResponse;
import com.supplychain.productservice.entity.Product;
import com.supplychain.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.crypto.Credentials;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final Web3j web3j;
    private final Credentials credentials;
    // private final ProductNFT productNFT; // Generated web3j wrapper for ProductNFT

    public ProductResponse registerProduct(ProductRegistrationRequest request) {
        // 1. Check if product with this serial number already exists
        if (productRepository.findBySerialNumber(request.getSerialNumber()).isPresent()) {
            throw new RuntimeException("Product with serial number '" + request.getSerialNumber() + "' already exists");
        }
        
        // 2. Save product in DB
        Product product = Product.builder()
                .serialNumber(request.getSerialNumber())
                .name(request.getName())
                .manufacturer(request.getManufacturer())
                .metadataUri(request.getMetadataUri())
                .registeredAt(Instant.now())
                .build();
        product = productRepository.save(product);

        // 2. Mint NFT on blockchain (stubbed, to be implemented with web3j wrapper)
        String nftTokenId = "0"; // TODO: Call ProductNFT.mintProduct(...)
        product.setNftTokenId(nftTokenId);
        productRepository.save(product);

        // 3. Build response
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

    public ProductResponse getProductBySerial(String serialNumber) {
        Product product = productRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new RuntimeException("Product not found"));
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
}
