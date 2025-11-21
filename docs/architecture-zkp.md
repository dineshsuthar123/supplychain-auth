# Architecture & ZKP Integration

## System Architecture
- **Blockchain Layer:**
  - ERC-721 ProductNFT contract for unique product authentication
  - ProductVerifier contract for on-chain verification (ZKP-ready)
- **Backend Microservices:**
  - Product Service: Registers products, mints NFTs, stores metadata in PostgreSQL
  - Verification Service: Verifies authenticity, interacts with ProductVerifier, uses Redis/MongoDB, ZKP stub
  - Event Service: Handles supply chain events via Kafka, stores audit trails in MongoDB
- **Cloud Infrastructure:**
  - Kubernetes, Ingress, HPA, managed databases, Redis, Kafka, Ethereum node

## Zero-Knowledge Proofs (ZKP)
- **Purpose:**
  - Enable privacy-preserving verification of product authenticity (e.g., prove ownership or supply chain event without revealing sensitive data)
- **Integration Points:**
  - ProductVerifier contract exposes a `verifyProduct` method accepting ZKP proof
  - Verification Service accepts ZKP in API, passes to contract
- **Recommended ZKP Libraries:**
  - [snarkjs](https://github.com/iden3/snarkjs) for proof generation (off-chain)
  - [ZoKrates](https://zokrates.github.io/introduction.html) for circuit design
- **Implementation Steps:**
  1. Define ZKP circuit for product verification (e.g., serial number hash, ownership)
  2. Generate trusted setup and keys
  3. Integrate proof generation in backend or mobile app
  4. Pass proof to smart contract for on-chain verification

## QR/NFC Verification Flow
1. User scans QR/NFC tag on product
2. Mobile app or web client generates ZKP (or retrieves from backend)
3. Sends proof to Verification Service API
4. Service calls ProductVerifier contract to validate proof
5. Returns verification result (without exposing private data)

---

For detailed API docs, see Swagger UI for each service.
