// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * SupplyPrint – ProductRegistrar
 * ================================
 * Anchors physical-fingerprint hashes on-chain so that any party can verify
 * a product's authenticity without trusting a central database.
 *
 * Deployed on Polygon Mumbai testnet (free MATIC via faucet).
 * Verify transactions at: https://mumbai.polygonscan.com
 *
 * Hash encoding
 * -------------
 * The feature_hash stored in PostgreSQL is a hex-encoded SHA-256 digest.
 * On-chain we store it as bytes32 for gas efficiency.
 * Java side: Numeric.hexStringToByteArray(featureHash) → bytes32
 */
contract ProductRegistrar {

    // productId (string key) → SHA-256 feature hash
    mapping(string => bytes32) public productHashes;

    // Track who registered a product (for audit)
    mapping(string => address) public productRegistrant;

    event ProductRegistered(string indexed productId, bytes32 hash, address registrant);
    event ProductUpdated(string indexed productId, bytes32 oldHash, bytes32 newHash);

    /**
     * Register or update a product fingerprint hash.
     *
     * @param productId   Unique product identifier (e.g. "MFG-BATCH001-SKU42")
     * @param hash        SHA-256 digest of (productId + embedding) as bytes32
     */
    function register(string calldata productId, bytes32 hash) external {
        require(bytes(productId).length > 0, "ProductRegistrar: empty productId");
        require(hash != bytes32(0),           "ProductRegistrar: zero hash");

        bytes32 existing = productHashes[productId];
        if (existing != bytes32(0)) {
            emit ProductUpdated(productId, existing, hash);
        }

        productHashes[productId]    = hash;
        productRegistrant[productId] = msg.sender;
        emit ProductRegistered(productId, hash, msg.sender);
    }

    /**
     * Pure view: returns true when the stored hash matches the supplied hash.
     *
     * @param productId  Product identifier to look up
     * @param hash       Hash to compare
     */
    function verify(string calldata productId, bytes32 hash) external view returns (bool) {
        return productHashes[productId] == hash && hash != bytes32(0);
    }

    /**
     * Returns the raw stored hash for a productId (bytes32(0) if not registered).
     */
    function getHash(string calldata productId) external view returns (bytes32) {
        return productHashes[productId];
    }
}
