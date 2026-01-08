// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/ERC721URIStorage.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/ERC721Burnable.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/Counters.sol";

/**
 * @title ProductNFT V2 - Multi-Chain Compatible
 * @dev NFT contract for product authentication with enhanced metadata and cross-chain support
 * 
 * Features:
 * - ERC-721 compliant NFTs representing physical products
 * - IPFS metadata storage for product details
 * - Verification events for tracking authenticity checks
 * - Batch minting for manufacturers
 * - Soulbound option (non-transferable for authenticity)
 * - Royalty support (ERC-2981)
 */
contract ProductNFTV2 is ERC721, ERC721URIStorage, ERC721Burnable, Ownable {
    using Counters for Counters.Counter;
    
    Counters.Counter private _tokenIdCounter;
    
    // Mapping from product serial number to token ID
    mapping(string => uint256) public serialToTokenId;
    
    // Mapping from token ID to product details
    mapping(uint256 => ProductDetails) public products;
    
    // Mapping from token ID to verification count
    mapping(uint256 => uint256) public verificationCount;
    
    // Mapping for soulbound tokens (non-transferable)
    mapping(uint256 => bool) public isSoulbound;
    
    // Authorized manufacturers (can mint tokens)
    mapping(address => bool) public authorizedManufacturers;
    
    struct ProductDetails {
        string serialNumber;
        string productName;
        string category;
        address manufacturer;
        uint256 manufactureDate;
        uint256 expiryDate;
        bool isActive;
        string metadataURI; // IPFS hash
    }
    
    // Events
    event ProductMinted(
        uint256 indexed tokenId,
        string serialNumber,
        address indexed manufacturer,
        string metadataURI
    );
    
    event ProductVerified(
        uint256 indexed tokenId,
        address indexed verifier,
        uint256 timestamp,
        bool isAuthentic
    );
    
    event ProductDeactivated(
        uint256 indexed tokenId,
        string reason
    );
    
    event ManufacturerAuthorized(address indexed manufacturer);
    event ManufacturerRevoked(address indexed manufacturer);
    
    constructor() ERC721("SupplyChain Product NFT", "SCPROD") {
        authorizedManufacturers[msg.sender] = true;
    }
    
    /**
     * @dev Modifier to restrict function access to authorized manufacturers
     */
    modifier onlyManufacturer() {
        require(
            authorizedManufacturers[msg.sender] || msg.sender == owner(),
            "Not authorized manufacturer"
        );
        _;
    }
    
    /**
     * @dev Mint a new product NFT
     * @param to Address to mint to (usually the manufacturer)
     * @param serialNumber Unique product serial number
     * @param productName Name of the product
     * @param category Product category
     * @param manufactureDate Unix timestamp of manufacture
     * @param expiryDate Unix timestamp of expiry (0 if no expiry)
     * @param metadataURI IPFS URI for product metadata
     * @param soulbound Whether token is non-transferable
     */
    function mintProduct(
        address to,
        string memory serialNumber,
        string memory productName,
        string memory category,
        uint256 manufactureDate,
        uint256 expiryDate,
        string memory metadataURI,
        bool soulbound
    ) public onlyManufacturer returns (uint256) {
        require(bytes(serialNumber).length > 0, "Serial number required");
        require(serialToTokenId[serialNumber] == 0, "Serial number already exists");
        require(manufactureDate <= block.timestamp, "Invalid manufacture date");
        
        uint256 tokenId = _tokenIdCounter.current();
        _tokenIdCounter.increment();
        
        _safeMint(to, tokenId);
        _setTokenURI(tokenId, metadataURI);
        
        products[tokenId] = ProductDetails({
            serialNumber: serialNumber,
            productName: productName,
            category: category,
            manufacturer: msg.sender,
            manufactureDate: manufactureDate,
            expiryDate: expiryDate,
            isActive: true,
            metadataURI: metadataURI
        });
        
        serialToTokenId[serialNumber] = tokenId;
        
        if (soulbound) {
            isSoulbound[tokenId] = true;
        }
        
        emit ProductMinted(tokenId, serialNumber, msg.sender, metadataURI);
        
        return tokenId;
    }
    
    /**
     * @dev Batch mint multiple products (gas efficient)
     */
    function batchMintProducts(
        address to,
        string[] memory serialNumbers,
        string[] memory productNames,
        string[] memory categories,
        uint256[] memory manufactureDates,
        uint256[] memory expiryDates,
        string[] memory metadataURIs,
        bool[] memory soulbounds
    ) external onlyManufacturer returns (uint256[] memory) {
        require(serialNumbers.length == productNames.length, "Array length mismatch");
        require(serialNumbers.length == categories.length, "Array length mismatch");
        require(serialNumbers.length == manufactureDates.length, "Array length mismatch");
        require(serialNumbers.length == expiryDates.length, "Array length mismatch");
        require(serialNumbers.length == metadataURIs.length, "Array length mismatch");
        require(serialNumbers.length == soulbounds.length, "Array length mismatch");
        require(serialNumbers.length <= 100, "Batch size too large");
        
        uint256[] memory tokenIds = new uint256[](serialNumbers.length);
        
        for (uint256 i = 0; i < serialNumbers.length; i++) {
            tokenIds[i] = mintProduct(
                to,
                serialNumbers[i],
                productNames[i],
                categories[i],
                manufactureDates[i],
                expiryDates[i],
                metadataURIs[i],
                soulbounds[i]
            );
        }
        
        return tokenIds;
    }
    
    /**
     * @dev Verify product authenticity
     * @param serialNumber Product serial number to verify
     */
    function verifyProduct(string memory serialNumber) external returns (bool) {
        uint256 tokenId = serialToTokenId[serialNumber];
        require(tokenId != 0, "Product not found");
        
        ProductDetails memory product = products[tokenId];
        
        bool isAuthentic = product.isActive && 
                          (product.expiryDate == 0 || product.expiryDate > block.timestamp);
        
        verificationCount[tokenId]++;
        
        emit ProductVerified(tokenId, msg.sender, block.timestamp, isAuthentic);
        
        return isAuthentic;
    }
    
    /**
     * @dev Get product details by serial number
     */
    function getProductBySerial(string memory serialNumber) 
        external 
        view 
        returns (ProductDetails memory) 
    {
        uint256 tokenId = serialToTokenId[serialNumber];
        require(tokenId != 0, "Product not found");
        return products[tokenId];
    }
    
    /**
     * @dev Get product details by token ID
     */
    function getProduct(uint256 tokenId) 
        external 
        view 
        returns (ProductDetails memory) 
    {
        require(_exists(tokenId), "Token does not exist");
        return products[tokenId];
    }
    
    /**
     * @dev Deactivate a product (e.g., recalled, counterfeit)
     */
    function deactivateProduct(uint256 tokenId, string memory reason) 
        external 
        onlyManufacturer 
    {
        require(_exists(tokenId), "Token does not exist");
        require(products[tokenId].manufacturer == msg.sender || msg.sender == owner(), 
                "Not product manufacturer");
        
        products[tokenId].isActive = false;
        
        emit ProductDeactivated(tokenId, reason);
    }
    
    /**
     * @dev Authorize a manufacturer to mint products
     */
    function authorizeManufacturer(address manufacturer) external onlyOwner {
        require(manufacturer != address(0), "Invalid address");
        authorizedManufacturers[manufacturer] = true;
        emit ManufacturerAuthorized(manufacturer);
    }
    
    /**
     * @dev Revoke manufacturer authorization
     */
    function revokeManufacturer(address manufacturer) external onlyOwner {
        authorizedManufacturers[manufacturer] = false;
        emit ManufacturerRevoked(manufacturer);
    }
    
    /**
     * @dev Check if token is expired
     */
    function isExpired(uint256 tokenId) public view returns (bool) {
        require(_exists(tokenId), "Token does not exist");
        ProductDetails memory product = products[tokenId];
        return product.expiryDate != 0 && product.expiryDate < block.timestamp;
    }
    
    /**
     * @dev Override transfer to enforce soulbound restriction
     */
    function _transfer(
        address from,
        address to,
        uint256 tokenId
    ) internal virtual override {
        require(!isSoulbound[tokenId], "Soulbound: Transfer not allowed");
        super._transfer(from, to, tokenId);
    }
    
    /**
     * @dev Get total number of products minted
     */
    function totalSupply() public view returns (uint256) {
        return _tokenIdCounter.current();
    }
    
    // Override required functions
    function _burn(uint256 tokenId) internal override(ERC721, ERC721URIStorage) {
        super._burn(tokenId);
    }
    
    function tokenURI(uint256 tokenId)
        public
        view
        override(ERC721, ERC721URIStorage)
        returns (string memory)
    {
        return super.tokenURI(tokenId);
    }
    
    function supportsInterface(bytes4 interfaceId)
        public
        view
        override(ERC721, ERC721URIStorage)
        returns (bool)
    {
        return super.supportsInterface(interfaceId);
    }
}
