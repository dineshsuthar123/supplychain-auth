const path = require("path");
const { config: loadEnv } = require("dotenv");

loadEnv({ path: path.resolve(__dirname, "..", ".env") });

require("@nomicfoundation/hardhat-toolbox");

const accounts = process.env.BLOCKCHAIN_PRIVATE_KEY
  ? [process.env.BLOCKCHAIN_PRIVATE_KEY]
  : [];

/** @type import("hardhat/config").HardhatUserConfig */
module.exports = {
  solidity: {
    version: "0.8.20",
    settings: { optimizer: { enabled: true, runs: 200 } },
  },
  paths: {
    sources:   "./contracts",
    tests:     "./test",
    cache:     "./cache",
    artifacts: "./artifacts",
  },
  networks: {
    hardhat: {},
    // Polygon Mumbai testnet (free MATIC via https://faucet.polygon.technology/)
    mumbai: {
      url: process.env.BLOCKCHAIN_RPC_URL || "https://rpc-mumbai.maticvigil.com",
      accounts,
      chainId: 80001,
      gasPrice: "auto",
    },
    // Polygon Amoy (successor to Mumbai – use if Mumbai is deprecated)
    amoy: {
      url: process.env.BLOCKCHAIN_AMOY_RPC_URL || "https://rpc-amoy.polygon.technology",
      accounts,
      chainId: 80002,
    },
  },
  etherscan: {
    apiKey: {
      polygonMumbai: process.env.POLYGONSCAN_API_KEY || "",
      polygonAmoy:   process.env.POLYGONSCAN_API_KEY || "",
    },
    customChains: [
      {
        network: "polygonAmoy",
        chainId: 80002,
        urls: {
          apiURL:     "https://api-amoy.polygonscan.com/api",
          browserURL: "https://amoy.polygonscan.com",
        },
      },
    ],
  },
};
