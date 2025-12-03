const path = require("path");
const { config: loadEnv } = require("dotenv");
const { ethers } = require("hardhat");

loadEnv({ path: path.resolve(__dirname, "..", "..", ".env") });

async function main() {
  const nftAddress = process.env.NFT_CONTRACT_ADDRESS || ethers.ZeroAddress;
  const zkVerifierAddress = process.env.ZK_VERIFIER_ADDRESS || ethers.ZeroAddress;

  if (!process.env.ETHEREUM_RPC_URL) {
    console.warn("[deploy] ETHEREUM_RPC_URL is not set. Using Hardhat default provider if running against localhost.");
  }

  if (!process.env.PRIVATE_KEY) {
    console.warn("[deploy] PRIVATE_KEY is not set. Transactions that require signing will fail on public networks.");
  }

  const ProductVerifier = await ethers.getContractFactory("ProductVerifier");
  const contract = await ProductVerifier.deploy(nftAddress, zkVerifierAddress);
  await contract.waitForDeployment();

  console.log("ProductVerifier deployed at:", await contract.getAddress());
  console.log("  NFT contract:", nftAddress);
  console.log("  ZK verifier:", zkVerifierAddress);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
