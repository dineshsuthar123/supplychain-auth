// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC721/extensions/ERC721URIStorage.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

/// @title ProductNFT - ERC-721 for Supply Chain Authentication
/// @notice Each product is minted as a unique NFT with metadata for traceability.
contract ProductNFT is ERC721URIStorage, Ownable {
    uint256 public nextTokenId;
    mapping(uint256 => string) public productSerials;

    event ProductMinted(address indexed to, uint256 indexed tokenId, string serial, string tokenURI);

    constructor() ERC721("ProductNFT", "PNFT") {}

    /// @notice Mint a new product NFT
    function mintProduct(address to, string memory serial, string memory tokenURI) external onlyOwner returns (uint256) {
        uint256 tokenId = nextTokenId++;
        _mint(to, tokenId);
        _setTokenURI(tokenId, tokenURI);
        productSerials[tokenId] = serial;
        emit ProductMinted(to, tokenId, serial, tokenURI);
        return tokenId;
    }

    /// @notice Get product serial by tokenId
    function getProductSerial(uint256 tokenId) external view returns (string memory) {
        return productSerials[tokenId];
    }
}
