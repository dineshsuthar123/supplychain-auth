/**
 * SupplyPrint – Deploy ProductRegistrar to Polygon Mumbai (or Amoy)
 *
 * Usage:
 *   npx hardhat run scripts/deploy.js --network mumbai
 *
 * Pre-requisites:
 *   1. Set BLOCKCHAIN_PRIVATE_KEY in .env (wallet funded with test MATIC)
 *      Faucet: https://faucet.polygon.technology/
 *   2. Set BLOCKCHAIN_RPC_URL (or use the default maticvigil URL)
 *
 * After deployment, update .env:
 *   BLOCKCHAIN_CONTRACT_ADDRESS=<address printed below>
 */
const path = require("path");
const fs   = require("fs");
const { config: loadEnv } = require("dotenv");
const { ethers } = require("hardhat");

loadEnv({ path: path.resolve(__dirname, "..", "..", ".env") });

async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying with account:", deployer.address);
  const balance = await ethers.provider.getBalance(deployer.address);
  console.log("Account balance:", ethers.formatEther(balance), "MATIC");

  const ProductRegistrar = await ethers.getContractFactory("ProductRegistrar");
  console.log("Deploying ProductRegistrar…");
  const contract = await ProductRegistrar.deploy();
  await contract.waitForDeployment();

  const address = await contract.getAddress();
  const receipt = await contract.deploymentTransaction().wait(1);
  console.log("─────────────────────────────────────────────────");
  console.log("✅  ProductRegistrar deployed!");
  console.log("    Address  :", address);
  console.log("    Tx hash  :", receipt.hash);
  console.log("    Block    :", receipt.blockNumber);
  console.log("    Polygonscan: https://mumbai.polygonscan.com/address/" + address);
  console.log("─────────────────────────────────────────────────");
  console.log("Add to .env:");
  console.log(`  BLOCKCHAIN_CONTRACT_ADDRESS=${address}`);

  // Auto-update .env file if it exists
  const envPath = path.resolve(__dirname, "..", "..", ".env");
  if (fs.existsSync(envPath)) {
    let envContent = fs.readFileSync(envPath, "utf8");
    const line = `BLOCKCHAIN_CONTRACT_ADDRESS=${address}`;
    if (envContent.includes("BLOCKCHAIN_CONTRACT_ADDRESS=")) {
      envContent = envContent.replace(/BLOCKCHAIN_CONTRACT_ADDRESS=.*/g, line);
    } else {
      envContent += `\n${line}\n`;
    }
    fs.writeFileSync(envPath, envContent);
    console.log("✅  .env updated automatically");
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

