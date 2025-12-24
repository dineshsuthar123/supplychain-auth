# =========================================================================
# 🔥 TIER-4 GODLY SERVER STARTUP SCRIPT (PowerShell) 🔥
# =========================================================================
# 
# Usage: .\start-godmode.ps1
# =========================================================================

Write-Host ""
Write-Host "🔥 TIER-4 GODLY SERVER STARTUP 🔥" -ForegroundColor Red
Write-Host "=================================" -ForegroundColor Red
Write-Host ""

Set-Location "$PSScriptRoot\backend\product-service"

# JVM Settings for Maximum Performance
$JAVA_OPTS = @(
    "-Xmx2g",
    "-Xms2g",
    "-XX:+UseZGC",
    "-XX:MaxGCPauseMillis=10",
    "-XX:+AlwaysPreTouch",
    "-XX:ConcGCThreads=4",
    "-XX:ParallelGCThreads=8",
    "-XX:+UseStringDeduplication",
    "-Dspring.profiles.active=godmode"
) -join " "

Write-Host "JVM Settings:" -ForegroundColor Yellow
Write-Host "  Heap: 2GB fixed (Xmx2g, Xms2g)" -ForegroundColor Cyan
Write-Host "  GC: ZGC (sub-10ms pauses)" -ForegroundColor Cyan
Write-Host "  Profile: godmode" -ForegroundColor Cyan
Write-Host ""
Write-Host "Starting server..." -ForegroundColor Green
Write-Host ""

$env:SPRING_PROFILES_ACTIVE = "godmode"

# Start with optimized JVM
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=$JAVA_OPTS"
