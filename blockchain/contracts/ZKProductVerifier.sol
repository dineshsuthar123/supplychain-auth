// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

import "./ProductVerifierV2.sol";

/**
 * @title ZKProductVerifier
 * @dev Zero-Knowledge proof based product verification for sensitive supply chain data
 * Enables verification without revealing sensitive product information
 */
contract ZKProductVerifier {
    
    // Verifying key for ZK-SNARKs (simplified for demo)
    struct VerifyingKey {
        uint256[2] alpha;
        uint256[2][2] beta;
        uint256[2][2] gamma;
        uint256[2][2] delta;
        uint256[][] ic;
    }
    
    struct Proof {
        uint256[2] a;
        uint256[2] b_0;
        uint256[2] b_1;
        uint256[2] c;
        uint256[2] h;
        uint256[2] k;
        uint256[] inputs;
    }
    
    VerifyingKey public verifyingKey;
    ProductVerifierV2 public immutable productVerifier;
    
    mapping(bytes32 => bool) public zkVerifiedProducts;
    
    event ZKProofVerified(bytes32 indexed commitment, bool verified);
    event SensitiveDataVerified(bytes32 indexed serialHash, bytes32 commitment);
    
    constructor(address _productVerifier) {
        productVerifier = ProductVerifierV2(_productVerifier);
    }
    
    /**
     * @dev Verify zero-knowledge proof for sensitive product data
     * @param proof The ZK-SNARK proof
     * @param commitment Hash commitment to sensitive data
     * @param serialNumber Public serial number
     */
    function verifyZKProof(
        Proof memory proof,
        bytes32 commitment,
        string calldata serialNumber
    ) external returns (bool) {
        // Simplified ZK verification (in production, use a proper ZK library)
        bytes32 serialHash = keccak256(abi.encodePacked(serialNumber));
        
        // Verify the product exists in the main contract
        (bool isRegistered,,,, bool isVerified) = productVerifier.verifyProduct(serialNumber);
        require(isRegistered, "Product not registered");
        
        // Simplified proof verification (replace with actual ZK-SNARK verification)
        bool proofValid = _verifyProof(proof, commitment);
        
        if (proofValid) {
            zkVerifiedProducts[commitment] = true;
            emit ZKProofVerified(commitment, true);
            emit SensitiveDataVerified(serialHash, commitment);
        }
        
        return proofValid;
    }
    
    /**
     * @dev Batch verify multiple ZK proofs
     */
    function batchVerifyZKProofs(
        Proof[] memory proofs,
        bytes32[] memory commitments,
        string[] memory serialNumbers
    ) external returns (bool[] memory results) {
        require(proofs.length == commitments.length && commitments.length == serialNumbers.length, "Array length mismatch");
        require(proofs.length <= 20, "Batch too large");
        
        results = new bool[](proofs.length);
        
        for (uint256 i = 0; i < proofs.length; i++) {
            bytes32 serialHash = keccak256(abi.encodePacked(serialNumbers[i]));
            (bool isRegistered,,,, bool isVerified) = productVerifier.verifyProduct(serialNumbers[i]);
            
            if (isRegistered) {
                bool proofValid = _verifyProof(proofs[i], commitments[i]);
                if (proofValid) {
                    zkVerifiedProducts[commitments[i]] = true;
                    emit SensitiveDataVerified(serialHash, commitments[i]);
                }
                results[i] = proofValid;
            }
        }
    }
    
    /**
     * @dev Check if sensitive data has been ZK-verified
     */
    function isZKVerified(bytes32 commitment) external view returns (bool) {
        return zkVerifiedProducts[commitment];
    }
    
    /**
     * @dev Generate commitment for sensitive data (helper function)
     */
    function generateCommitment(
        string calldata sensitiveData,
        uint256 nonce
    ) external pure returns (bytes32) {
        return keccak256(abi.encodePacked(sensitiveData, nonce));
    }
    
    /**
     * @dev Internal proof verification (simplified)
     * In production, integrate with a proper ZK-SNARK library like arkworks or circom
     */
    function _verifyProof(Proof memory proof, bytes32 commitment) internal pure returns (bool) {
        // Simplified verification logic
        // In production, this would use proper elliptic curve pairing operations
        
        // Check proof structure validity
        if (proof.a.length != 2 || proof.b_0.length != 2 || proof.c.length != 2) {
            return false;
        }
        
        // Simplified verification based on commitment
        uint256 commitmentUint = uint256(commitment);
        
        // Mock verification - replace with actual ZK verification
        return (commitmentUint % 97) != 0; // Simple deterministic check
    }
    
    /**
     * @dev Update verifying key (only owner)
     */
    function updateVerifyingKey(VerifyingKey memory newKey) external {
        // In production, add proper access control
        verifyingKey = newKey;
    }
}

/**
 * @title SupplyChainNFT
 * @dev NFT representation of verified products for enhanced traceability
 */
import "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract SupplyChainNFT is ERC721, Ownable {
    
    struct ProductNFT {
        string serialNumber;
        address manufacturer;
        uint256 timestamp;
        string metadataURI;
        bool isVerified;
    }
    
    mapping(uint256 => ProductNFT) public productNFTs;
    mapping(string => uint256) public serialToTokenId;
    
    uint256 private nextTokenId = 1;
    
    ProductVerifierV2 public immutable productVerifier;
    
    event ProductNFTMinted(uint256 indexed tokenId, string serialNumber, address manufacturer);
    event ProductNFTVerified(uint256 indexed tokenId, bool verified);
    
    constructor(address _productVerifier) ERC721("SupplyChain Product", "SCP") {
        productVerifier = ProductVerifierV2(_productVerifier);
    }
    
    /**
     * @dev Mint NFT for verified product
     */
    function mintProductNFT(
        string calldata serialNumber,
        string calldata metadataURI,
        address to
    ) external returns (uint256) {
        // Verify product exists and is registered
        (bool isRegistered, uint256 timestamp,, address manufacturer, bool isVerified) = 
            productVerifier.verifyProduct(serialNumber);
        
        require(isRegistered, "Product not registered");
        require(serialToTokenId[serialNumber] == 0, "NFT already minted for this product");
        
        uint256 tokenId = nextTokenId++;
        
        productNFTs[tokenId] = ProductNFT({
            serialNumber: serialNumber,
            manufacturer: manufacturer,
            timestamp: timestamp,
            metadataURI: metadataURI,
            isVerified: isVerified
        });
        
        serialToTokenId[serialNumber] = tokenId;
        
        _mint(to, tokenId);
        
        emit ProductNFTMinted(tokenId, serialNumber, manufacturer);
        
        return tokenId;
    }
    
    /**
     * @dev Get NFT metadata URI
     */
    function tokenURI(uint256 tokenId) public view override returns (string memory) {
        require(_exists(tokenId), "Token does not exist");
        return productNFTs[tokenId].metadataURI;
    }
    
    /**
     * @dev Mark NFT as verified
     */
    function markNFTVerified(uint256 tokenId) external onlyOwner {
        require(_exists(tokenId), "Token does not exist");
        productNFTs[tokenId].isVerified = true;
        emit ProductNFTVerified(tokenId, true);
    }
}
