#!/usr/bin/env bash
# SupplyPrint – Smart Contract Deploy Script
# ============================================
# Compiles and deploys ProductRegistrar.sol to Polygon Mumbai (or Amoy) testnet.
# Automatically writes BLOCKCHAIN_CONTRACT_ADDRESS to .env in the repo root.
#
# Requirements:
#   - Node.js >= 18 (npm / npx)
#   - BLOCKCHAIN_PRIVATE_KEY in .env  (no 0x prefix needed)
#   - BLOCKCHAIN_RPC_URL in .env      (e.g. https://rpc-mumbai.maticvigil.com)
#   - Sufficient MATIC for gas in the deployer wallet
#
# Usage:
#   ./deploy-contract.sh                  # defaults to mumbai
#   ./deploy-contract.sh amoy             # deploy to Amoy testnet

set -euo pipefail

NETWORK="${1:-mumbai}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BLOCKCHAIN_DIR="$SCRIPT_DIR/blockchain"
ENV_FILE="$SCRIPT_DIR/.env"

echo "══════════════════════════════════════════════════════"
echo "  SupplyPrint Contract Deployer"
echo "  Network: $NETWORK"
echo "══════════════════════════════════════════════════════"

# ── Load .env ──────────────────────────────────────────────────────────────
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
  echo "[✓] .env loaded"
else
  echo "[!] WARNING: .env not found at $ENV_FILE"
fi

# ── Validate required vars ─────────────────────────────────────────────────
: "${BLOCKCHAIN_PRIVATE_KEY:?BLOCKCHAIN_PRIVATE_KEY must be set in .env}"
: "${BLOCKCHAIN_RPC_URL:?BLOCKCHAIN_RPC_URL must be set in .env}"

# ── Install Hardhat deps ───────────────────────────────────────────────────
cd "$BLOCKCHAIN_DIR"
echo "[…] Installing npm dependencies..."
npm install --silent

# ── Compile ────────────────────────────────────────────────────────────────
echo "[…] Compiling contracts..."
npx hardhat compile

# ── Deploy ─────────────────────────────────────────────────────────────────
echo "[…] Deploying ProductRegistrar to $NETWORK..."
npx hardhat run scripts/deploy.js --network "$NETWORK"

# ── Verify contract address was written ───────────────────────────────────
if grep -q "BLOCKCHAIN_CONTRACT_ADDRESS" "$ENV_FILE" 2>/dev/null; then
  echo "[✓] BLOCKCHAIN_CONTRACT_ADDRESS written to .env"
else
  echo "[!] BLOCKCHAIN_CONTRACT_ADDRESS not found in .env – check deploy.js output"
fi

echo ""
echo "══════════════════════════════════════════════════════"
echo "  Deployment complete!"
echo "  Next steps:"
echo "    1. Copy .env values into your docker-compose env or secrets"
echo "    2. Restart product-service:  docker compose up -d product-service"
echo "══════════════════════════════════════════════════════"
