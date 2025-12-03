# Blockchain Layer

This folder now doubles as a standalone [Hardhat](https://hardhat.org/) workspace so you can compile and deploy the smart contracts without mixing blockchain tooling into the main repository.

```
blockchain/
├── contracts/
├── scripts/
├── test/
├── hardhat.config.js
├── package.json
└── README.md
```

## Quick start

1. Install dependencies

	```powershell
	cd blockchain
	npm install
	```

2. Make sure the repository root `.env` contains the usual backend secrets **plus** the Ethereum values that Hardhat expects:

	```dotenv
	ETHEREUM_RPC_URL=https://sepolia.infura.io/v3/<project_id>
	PRIVATE_KEY=0xabc123...
	NFT_CONTRACT_ADDRESS=0x0000000000000000000000000000000000000000   # optional seed value
	ZK_VERIFIER_ADDRESS=0x0000000000000000000000000000000000000000   # optional seed value
	```

	The Hardhat config automatically loads the root `.env`, so you do **not** need to create another env file inside `blockchain/`.

3. Compile contracts

	```powershell
	npm run compile
	```

4. Deploy ProductVerifier (examples)

	```powershell
	# Local in-memory Hardhat network
	npm run deploy:local

	# Sepolia testnet (requires ETHEREUM_RPC_URL + PRIVATE_KEY)
	npm run deploy:sepolia
	```

	The deploy script uses `NFT_CONTRACT_ADDRESS` and `ZK_VERIFIER_ADDRESS` from the env file. If you leave them blank, both constructor arguments default to `address(0)` so you can wire them later.

## Todo / ideas

- Flesh out automated tests under `test/`.
- Add deployment scripts for ProductNFT + verifier combinations.
- Integrate a proper ZKP verifier once the circuits are finalized.
