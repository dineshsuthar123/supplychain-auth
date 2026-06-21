# SupplyPrint – Smart Contract Deploy Script (Windows PowerShell)
# ================================================================
# Compiles and deploys ProductRegistrar.sol to Polygon Mumbai (or Amoy) testnet.
# Automatically writes BLOCKCHAIN_CONTRACT_ADDRESS to .env in the repo root.
#
# Requirements:
#   - Node.js >= 18 (npm / npx in PATH)
#   - BLOCKCHAIN_PRIVATE_KEY in .env  (no 0x prefix needed)
#   - BLOCKCHAIN_RPC_URL in .env      (e.g. https://rpc-mumbai.maticvigil.com)
#   - Sufficient MATIC for gas in the deployer wallet
#
# Usage (PowerShell):
#   .\deploy-contract.ps1               # defaults to mumbai
#   .\deploy-contract.ps1 -Network amoy # deploy to Amoy testnet

param(
    [string]$Network = "mumbai"
)

$ErrorActionPreference = "Stop"

$RepoRoot      = $PSScriptRoot
$BlockchainDir = Join-Path $RepoRoot "blockchain"
$EnvFile       = Join-Path $RepoRoot ".env"

Write-Host "══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  SupplyPrint Contract Deployer" -ForegroundColor Cyan
Write-Host "  Network: $Network" -ForegroundColor Cyan
Write-Host "══════════════════════════════════════════════════════" -ForegroundColor Cyan

# ── Load .env ──────────────────────────────────────────────────────────────
if (Test-Path $EnvFile) {
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') {
            $key   = $Matches[1].Trim()
            $value = $Matches[2].Trim().Trim('"').Trim("'")
            [System.Environment]::SetEnvironmentVariable($key, $value, "Process")
        }
    }
    Write-Host "[✓] .env loaded" -ForegroundColor Green
} else {
    Write-Warning ".env not found at $EnvFile"
}

# ── Validate required vars ─────────────────────────────────────────────────
$privateKey = [System.Environment]::GetEnvironmentVariable("BLOCKCHAIN_PRIVATE_KEY", "Process")
$rpcUrl     = [System.Environment]::GetEnvironmentVariable("BLOCKCHAIN_RPC_URL",     "Process")

if (-not $privateKey) { throw "BLOCKCHAIN_PRIVATE_KEY must be set in .env" }
if (-not $rpcUrl)     { throw "BLOCKCHAIN_RPC_URL must be set in .env" }

# ── Install Hardhat deps ───────────────────────────────────────────────────
Set-Location $BlockchainDir
Write-Host "[…] Installing npm dependencies..."
npm install --silent

# ── Compile ────────────────────────────────────────────────────────────────
Write-Host "[…] Compiling contracts..."
npx hardhat compile

# ── Deploy ─────────────────────────────────────────────────────────────────
Write-Host "[…] Deploying ProductRegistrar to $Network..."
$env:HARDHAT_NETWORK = $Network
npx hardhat run scripts/deploy.js --network $Network

# ── Verify .env was updated ────────────────────────────────────────────────
if (Select-String -Path $EnvFile -Pattern "BLOCKCHAIN_CONTRACT_ADDRESS" -Quiet) {
    Write-Host "[✓] BLOCKCHAIN_CONTRACT_ADDRESS written to .env" -ForegroundColor Green
} else {
    Write-Warning "BLOCKCHAIN_CONTRACT_ADDRESS not found in .env – check deploy.js output"
}

Write-Host ""
Write-Host "══════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Deployment complete!" -ForegroundColor Green
Write-Host "  Next steps:" -ForegroundColor Cyan
Write-Host "    1. Copy .env values into your docker-compose env or secrets"
Write-Host "    2. Restart product-service:  docker compose up -d product-service"
Write-Host "══════════════════════════════════════════════════════" -ForegroundColor Cyan

Set-Location $RepoRoot
