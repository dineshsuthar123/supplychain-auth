[CmdletBinding()]
param([ValidatePattern('^[a-z0-9][a-z0-9_-]*$')][string]$ProjectName='lotline-local')
$ErrorActionPreference='Stop'
$TaskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$StatePath=Join-Path $TaskRoot "artifacts/$ProjectName-launch.json"
if (-not (Test-Path -LiteralPath $StatePath)) { throw 'No recorded local launch exists for this project.' }
$State=Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
if ($State.project -ne $ProjectName) { throw 'Launch record does not match the selected project.' }
$Process=Get-Process -Id $State.frontendPid -ErrorAction SilentlyContinue
if ($Process -and $Process.ProcessName -eq 'node' -and $Process.StartTime.ToUniversalTime().Ticks.ToString() -eq $State.frontendStartTicks) {
    Stop-Process -Id $Process.Id
}
& docker compose --project-directory $TaskRoot --project-name $ProjectName --env-file $State.envFile stop --timeout 25
if ($LASTEXITCODE -ne 0) { throw 'Docker could not stop this project.' }
Write-Host 'Lotline stopped. Its PostgreSQL volume and records are retained.'
