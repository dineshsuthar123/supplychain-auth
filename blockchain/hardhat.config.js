const path = require("path");
const { config: loadEnv } = require("dotenv");

loadEnv({ path: path.resolve(__dirname, "..", ".env") });

require("@nomicfoundation/hardhat-toolbox");

const accounts = process.env.PRIVATE_KEY ? [process.env.PRIVATE_KEY] : [];

/** @type import("hardhat/config").HardhatUserConfig */
module.exports = {
  solidity: "0.8.20",
  paths: {
    sources: "./contracts",
    tests: "./test",
    cache: "./cache",
    artifacts: "./artifacts"
  },
  networks: {
    hardhat: {},
    sepolia: {
      url: process.env.ETHEREUM_RPC_URL || "",
      accounts,
    },
  },
};
