package com.supplychain.common.blockchain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

/**
 * Multi-chain blockchain network configuration
 * Supports Ethereum, Polygon, BSC, and Solana
 */
@Getter
@AllArgsConstructor
public enum BlockchainNetwork {
    
    // Ethereum Networks
    ETHEREUM_MAINNET(
        "Ethereum Mainnet",
        "https://mainnet.infura.io/v3/",
        1L,
        "ETH",
        15.0,  // Average gas price in Gwei
        15,    // Block time in seconds
        true,  // Production ready
        "High security, high cost. Best for luxury goods and high-value products"
    ),
    
    ETHEREUM_SEPOLIA(
        "Ethereum Sepolia Testnet",
        "https://sepolia.infura.io/v3/",
        11155111L,
        "SepoliaETH",
        0.0,
        12,
        false,
        "Testnet for development and testing"
    ),
    
    // Polygon Networks
    POLYGON_MAINNET(
        "Polygon Mainnet",
        "https://polygon-rpc.com",
        137L,
        "MATIC",
        0.001,  // Very low gas cost
        2,
        true,
        "Low cost, high speed. Best for mass-market products. 99% cheaper than Ethereum"
    ),
    
    POLYGON_MUMBAI(
        "Polygon Mumbai Testnet",
        "https://rpc-mumbai.maticvigil.com",
        80001L,
        "MATIC",
        0.0,
        2,
        false,
        "Polygon testnet for development"
    ),
    
    // Binance Smart Chain
    BSC_MAINNET(
        "Binance Smart Chain",
        "https://bsc-dataseed.binance.org",
        56L,
        "BNB",
        0.1,
        3,
        true,
        "Medium cost, fast finality. Good for mid-tier products"
    ),
    
    BSC_TESTNET(
        "BSC Testnet",
        "https://data-seed-prebsc-1-s1.binance.org:8545",
        97L,
        "tBNB",
        0.0,
        3,
        false,
        "BSC testnet for development"
    ),
    
    // Arbitrum (Ethereum Layer 2)
    ARBITRUM_ONE(
        "Arbitrum One",
        "https://arb1.arbitrum.io/rpc",
        42161L,
        "ETH",
        0.01,
        0.25,
        true,
        "Ethereum Layer 2 with low fees and fast transactions"
    ),
    
    // Optimism (Ethereum Layer 2)
    OPTIMISM_MAINNET(
        "Optimism Mainnet",
        "https://mainnet.optimism.io",
        10L,
        "ETH",
        0.01,
        2,
        true,
        "Ethereum Layer 2 with EVM compatibility"
    );
    
    private final String name;
    private final String rpcUrl;
    private final Long chainId;
    private final String nativeCurrency;
    private final Double avgGasCostUSD;  // Average transaction cost in USD
    private final Integer blockTimeSeconds;
    private final Boolean productionReady;
    private final String recommendation;
    
    /**
     * Get network by chain ID
     */
    public static BlockchainNetwork fromChainId(Long chainId) {
        for (BlockchainNetwork network : values()) {
            if (network.getChainId().equals(chainId)) {
                return network;
            }
        }
        throw new IllegalArgumentException("Unknown chain ID: " + chainId);
    }
    
    /**
     * Get recommended network based on product value
     * @param productValueUSD Product value in USD
     * @return Most cost-effective network
     */
    public static BlockchainNetwork getRecommendedNetwork(double productValueUSD) {
        if (productValueUSD > 10000) {
            // High-value products: Use Ethereum for maximum security
            return ETHEREUM_MAINNET;
        } else if (productValueUSD > 1000) {
            // Mid-value products: Use BSC or Arbitrum
            return BSC_MAINNET;
        } else {
            // Mass-market products: Use Polygon for lowest cost
            return POLYGON_MAINNET;
        }
    }
    
    /**
     * Get all production-ready networks
     */
    public static BlockchainNetwork[] getProductionNetworks() {
        return java.util.Arrays.stream(values())
                .filter(BlockchainNetwork::getProductionReady)
                .toArray(BlockchainNetwork[]::new);
    }
    
    /**
     * Calculate cost-effectiveness score
     * Lower score = better cost-effectiveness
     */
    public double getCostEffectivenessScore() {
        // Score based on gas cost and block time
        return avgGasCostUSD * Math.log(blockTimeSeconds + 1);
    }
    
    /**
     * Check if network is a testnet
     */
    public boolean isTestnet() {
        return !productionReady;
    }
    
    /**
     * Get full RPC URL with Infura API key
     */
    public String getRpcUrlWithKey(String infuraApiKey) {
        if (rpcUrl.contains("infura")) {
            return rpcUrl + infuraApiKey;
        }
        return rpcUrl;
    }
}
