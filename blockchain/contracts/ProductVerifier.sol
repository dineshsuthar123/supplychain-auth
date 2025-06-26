// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/// @title ProductVerifier - Gas-Optimized Verification Contract
/// @notice Verifies authenticity of product NFTs, ZKP-ready for privacy.
interface IZKVerifier {
    function verifyProof(bytes calldata proof, bytes32 root, bytes32 leaf) external view returns (bool);
}

contract ProductVerifier {
    address public nftContract;
    address public zkVerifier;
    mapping(uint256 => bool) public verified;

    event ProductVerified(uint256 indexed tokenId, address indexed verifier);

    constructor(address _nftContract, address _zkVerifier) {
        nftContract = _nftContract;
        zkVerifier = _zkVerifier;
    }

    /// @notice Verify product authenticity (ZKP integration ready)
    function verifyProduct(uint256 tokenId, bytes calldata proof, bytes32 root, bytes32 leaf) external {
        require(!verified[tokenId], "Already verified");
        // ZKP verification (mocked, to be implemented)
        if (zkVerifier != address(0)) {
            require(IZKVerifier(zkVerifier).verifyProof(proof, root, leaf), "Invalid ZKP proof");
        }
        verified[tokenId] = true;
        emit ProductVerified(tokenId, msg.sender);
    }
}
