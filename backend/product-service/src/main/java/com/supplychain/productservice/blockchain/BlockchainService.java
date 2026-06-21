package com.supplychain.productservice.blockchain;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.ClientConnectionException;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Wraps all Polygon/Ethereum blockchain interactions for SupplyPrint.
 *
 * <p>Resilience strategy:
 * <ul>
 *   <li>Circuit breaker "blockchain-rpc" – opens after 50% failure over 10 calls;
 *       half-open state allows 3 test calls.</li>
 *   <li>Retry – 3 attempts with 1s/2s/4s exponential back-off for RPC timeouts.</li>
 *   <li>Fallback – if circuit is open, returns an empty Optional; the caller
 *       relies on the BlockchainOutboxProcessor to retry later.</li>
 * </ul>
 */
@Service
public class BlockchainService {

    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    // Resilience4j instance names (must match application.properties)
    private static final String CB_NAME    = "blockchain-rpc";
    private static final String RETRY_NAME = "blockchain-rpc";

    private final Web3j web3j;

    @Value("${blockchain.private-key:}")
    private String privateKey;

    @Value("${blockchain.contract-address:}")
    private String contractAddress;

    @Value("${blockchain.enabled:true}")
    private boolean blockchainEnabled;

    private Credentials credentials;

    public BlockchainService(Web3j web3j) {
        this.web3j = web3j;
    }

    @PostConstruct
    public void init() {
        if (!blockchainEnabled) {
            log.warn("Blockchain integration is DISABLED (blockchain.enabled=false)");
            return;
        }
        if (privateKey == null || privateKey.isBlank()) {
            log.warn("BLOCKCHAIN_PRIVATE_KEY not set – on-chain writes will fail. Set it in .env to enable.");
            return;
        }
        credentials = Credentials.create(privateKey);
        log.info("BlockchainService initialised. Wallet: {}", credentials.getAddress());
        if (contractAddress == null || contractAddress.isBlank()) {
            log.warn("BLOCKCHAIN_CONTRACT_ADDRESS not set – deploy the contract first with: " +
                     "cd blockchain && npx hardhat run scripts/deploy.js --network mumbai");
        }
    }

    /**
     * Registers a product fingerprint hash on-chain.
     *
     * @param productId   unique product identifier
     * @param featureHash hex-encoded SHA-256 digest (64 chars)
     * @return TransactionReceipt from the confirmed transaction
     */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "registerFallback")
    @Retry(name = RETRY_NAME)
    public TransactionReceipt registerProductOnChain(String productId, String featureHash) throws Exception {
        assertReady();
        ProductRegistrarContract contract = loadContract();
        // Convert hex string to bytes32
        byte[] hashBytes = toBytes32(featureHash);
        TransactionReceipt receipt = contract.register(productId, hashBytes).send();
        log.info("Registered on-chain: productId={} txHash={} block={}",
                productId, receipt.getTransactionHash(), receipt.getBlockNumber());
        return receipt;
    }

    /**
     * Fallback for registerProductOnChain when the circuit is open or retries exhausted.
     * Returns null so the caller can detect the failure and rely on the outbox processor.
     */
    @SuppressWarnings("unused")
    public TransactionReceipt registerFallback(String productId, String featureHash, Throwable ex) {
        log.warn("Blockchain circuit open or retries exhausted for productId={}: {}", productId, ex.getMessage());
        return null;  // Caller must check for null and leave outbox PENDING
    }

    /**
     * Verifies that the stored on-chain hash matches the supplied featureHash.
     *
     * @param productId   product identifier
     * @param featureHash hex-encoded SHA-256 digest
     * @return true if hashes match; false if mismatch or product not registered
     */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "verifyFallback")
    @Retry(name = RETRY_NAME)
    public boolean verifyOnChain(String productId, String featureHash) throws Exception {
        assertReady();
        ProductRegistrarContract contract = loadContract();
        byte[] hashBytes = toBytes32(featureHash);
        Boolean result = contract.verify(productId, hashBytes).send();
        log.debug("on-chain verify productId={} result={}", productId, result);
        return Boolean.TRUE.equals(result);
    }

    /**
     * Fallback: when chain is unreachable we return null (meaning "unknown") so
     * the verification endpoint can set blockchainConfirmed=false with a warning.
     */
    @SuppressWarnings("unused")
    public Boolean verifyFallback(String productId, String featureHash, Throwable ex) {
        log.warn("Blockchain circuit open for verification productId={}: {}", productId, ex.getMessage());
        return null;  // null = cannot confirm
    }

    /**
     * Simple liveness check – fetches the current block number.
     * Used by the HealthIndicator.
     */
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "pingFallback")
    public long getLatestBlockNumber() throws Exception {
        EthBlockNumber response = web3j.ethBlockNumber().send();
        return response.getBlockNumber().longValue();
    }

    @SuppressWarnings("unused")
    public long pingFallback(Throwable ex) {
        log.debug("Blockchain RPC ping failed: {}", ex.getMessage());
        return -1L;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProductRegistrarContract loadContract() {
        return ProductRegistrarContract.load(
                contractAddress,
                web3j,
                credentials,
                new DefaultGasProvider()
        );
    }

    private void assertReady() {
        if (!blockchainEnabled) throw new IllegalStateException("Blockchain integration disabled");
        if (credentials == null) throw new IllegalStateException("Blockchain credentials not configured");
        if (contractAddress == null || contractAddress.isBlank())
            throw new IllegalStateException("Contract address not configured – deploy the contract first");
    }

    /**
     * Converts a 64-char hex digest to a 32-byte array (bytes32 in Solidity).
     */
    private static byte[] toBytes32(String hexHash) {
        // Strip "0x" prefix if present
        String clean = hexHash.startsWith("0x") || hexHash.startsWith("0X")
                ? hexHash.substring(2) : hexHash;
        byte[] raw = Numeric.hexStringToByteArray(clean);
        if (raw.length == 32) return raw;
        // Pad to 32 bytes
        byte[] padded = new byte[32];
        System.arraycopy(raw, 0, padded, 32 - raw.length, raw.length);
        return padded;
    }

    public boolean isBlockchainEnabled() {
        return blockchainEnabled && credentials != null && contractAddress != null && !contractAddress.isBlank();
    }
}
