[CmdletBinding()]
param(
    [ValidateRange(1024,65535)][int]$ApiPort = 8080,
    [ValidateRange(1024,65535)][int]$DatabasePort = 5433,
    [ValidateRange(1024,65535)][int]$FrontendPort = 4173,
    [ValidatePattern('^[a-z0-9][a-z0-9_-]*$')][string]$ProjectName = 'lotline-local',
    [string]$EnvFile = '.env'
)
$ErrorActionPreference = 'Stop'
$TaskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$FrontendRoot = Join-Path $TaskRoot 'frontend'
$PreviousDirectory = Get-Location
$ChangedEnvironment = @{}
function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed. Check the output above." }
}
function New-LocalSecret {
    $Bytes = New-Object byte[] 32
    $Generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $Generator.GetBytes($Bytes) } finally { $Generator.Dispose() }
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
}
try {
    Set-Location -LiteralPath $TaskRoot
    foreach ($Program in @('docker','node','npm.cmd')) { Get-Command $Program -ErrorAction Stop | Out-Null }
    $NodeMajor = [int]((& node --version).TrimStart('v').Split('.')[0])
    if ($NodeMajor -ne 22) { throw 'Install Node 22 before starting Lotline.' }
    Invoke-Checked 'docker' @('info','--format','{{.ServerVersion}}')
    foreach ($Port in @($ApiPort,$DatabasePort,$FrontendPort)) {
        if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
            throw "Port $Port is already in use. Choose another port or stop your previous Lotline instance."
        }
    }
    if (@($ApiPort,$DatabasePort,$FrontendPort | Select-Object -Unique).Count -ne 3) {
        throw 'Choose three different ports for the API, database and frontend.'
    }
    $ExistingVite = Get-CimInstance Win32_Process -Filter "Name='node.exe'" | Where-Object {
        $_.CommandLine -like "*$FrontendRoot*node_modules*vite*"
    }
    if ($ExistingVite) { throw 'Stop the existing frontend for this checkout before reinstalling its dependencies.' }
    $ResolvedEnv = [IO.Path]::GetFullPath((Join-Path $TaskRoot $EnvFile))
    if (-not (Test-Path -LiteralPath $ResolvedEnv)) {
        $Parent = Split-Path -Parent $ResolvedEnv
        if (-not (Test-Path -LiteralPath $Parent)) { New-Item -ItemType Directory -Path $Parent -Force | Out-Null }
        $Lines = @("DB_PASSWORD=$(New-LocalSecret)","JWT_SECRET=$(New-LocalSecret)","DEMO_PASSWORD=$(New-LocalSecret)",
            "DB_HOST=localhost","DB_PORT=$DatabasePort","API_PORT=$ApiPort","DB_NAME=lotline","DB_USER=lotline",
            "TRUSTED_ORIGINS=http://127.0.0.1:$FrontendPort,http://localhost:$FrontendPort,http://localhost:5173","SECURE_COOKIES=false")
        [IO.File]::WriteAllLines($ResolvedEnv,$Lines,(New-Object Text.UTF8Encoding($false)))
        Write-Host 'Created local credentials in the ignored environment file. No accounts or demo stock are seeded.'
    }
    $Settings = @{}
    foreach ($Line in Get-Content -LiteralPath $ResolvedEnv) {
        if ($Line -match '^([A-Z_]+)=(.*)$') { $Settings[$Matches[1]]=$Matches[2].Trim().Trim('"').Trim("'") }
    }
    foreach ($Name in @('JWT_SECRET','DB_PASSWORD')) {
        if (-not $Settings[$Name] -or $Settings[$Name].StartsWith('replace-')) { throw "Set a real $Name in your environment file." }
    }
    if ($Settings['JWT_SECRET'].Length -lt 32) { throw 'JWT_SECRET must contain at least 32 characters.' }
    $Overrides = @{ API_PORT="$ApiPort"; DB_PORT="$DatabasePort"; LOTLINE_API_TARGET="http://127.0.0.1:$ApiPort";
        TRUSTED_ORIGINS="$($Settings['TRUSTED_ORIGINS']),http://localhost:$FrontendPort,http://127.0.0.1:$FrontendPort" }
    foreach ($Name in $Overrides.Keys) {
        $ChangedEnvironment[$Name]=[Environment]::GetEnvironmentVariable($Name,'Process')
        [Environment]::SetEnvironmentVariable($Name,$Overrides[$Name],'Process')
    }
    Invoke-Checked 'docker' @('compose','--project-name',$ProjectName,'--env-file',$ResolvedEnv,'up','--build','--detach')
    $Ready=$false
    for ($Attempt=0;$Attempt -lt 60;$Attempt++) {
        try {
            $Health=Invoke-RestMethod -Uri "http://127.0.0.1:$ApiPort/actuator/health/readiness" -TimeoutSec 3
            if ($Health.status -eq 'UP') { $Ready=$true; break }
        } catch {}
        Start-Sleep -Seconds 2
    }
    if (-not $Ready) { throw 'The API did not become ready. Inspect docker compose logs.' }
    Set-Location -LiteralPath $FrontendRoot
    Invoke-Checked 'npm.cmd' @('ci','--no-fund')
    Invoke-Checked 'npm.cmd' @('run','build')
    $ArtifactRoot=Join-Path $TaskRoot 'artifacts'
    New-Item -ItemType Directory -Path $ArtifactRoot -Force | Out-Null
    $PreviewCommand="npm run preview -- --host 127.0.0.1 --port $FrontendPort --strictPort"
    $Helper=Start-Process -FilePath $env:COMSPEC -ArgumentList @('/d','/c',$PreviewCommand) -WorkingDirectory $FrontendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $ArtifactRoot "$ProjectName-frontend.log") -RedirectStandardError (Join-Path $ArtifactRoot "$ProjectName-frontend-error.log")
    $FrontendReady=$false
    for ($Attempt=0;$Attempt -lt 20;$Attempt++) {
        try { $Response=Invoke-WebRequest -Uri "http://127.0.0.1:$FrontendPort" -UseBasicParsing -TimeoutSec 2; if ($Response.StatusCode -eq 200) { $FrontendReady=$true; break } } catch {}
        Start-Sleep -Seconds 1
    }
    if (-not $FrontendReady) { throw 'The frontend did not start. Inspect its private log in artifacts.' }
    $Listener=Get-NetTCPConnection -State Listen -LocalPort $FrontendPort | Select-Object -First 1
    $NodeProcess=Get-Process -Id $Listener.OwningProcess
    $State=@{ project=$ProjectName; envFile=$ResolvedEnv; frontendPort=$FrontendPort; frontendPid=$NodeProcess.Id; frontendStartTicks=$NodeProcess.StartTime.ToUniversalTime().Ticks.ToString() }
    $State | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ArtifactRoot "$ProjectName-launch.json") -Encoding UTF8
    Write-Host "Lotline is ready: http://127.0.0.1:$FrontendPort"
    Write-Host 'Click Start a workspace and choose your own account password.'
    Write-Host "Stop later with: .\scripts\stop-local.ps1 -ProjectName $ProjectName"
} finally {
    foreach ($Name in $ChangedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($Name,$ChangedEnvironment[$Name],'Process') }
    Set-Location -LiteralPath $PreviousDirectory
}
