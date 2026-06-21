# =============================================================================
# Start product-service with REALISTIC profile
# Requires: PostgreSQL, Redis, Kafka running (docker-compose up -d)
# =============================================================================
Write-Host "============================================"
Write-Host "  Starting REALISTIC mode"
Write-Host "  PostgreSQL + Redis + Kafka (real stack)"
Write-Host "============================================"
Write-Host ""

Push-Location "$PSScriptRoot\backend\product-service"

$env:SPRING_PROFILES_ACTIVE = "realistic"
$env:JAVA_OPTS = "-Xmx1g -Xms512m -XX:+UseG1GC"

Write-Host "Profiles: $env:SPRING_PROFILES_ACTIVE"
Write-Host "JVM: $env:JAVA_OPTS"
Write-Host ""

mvn spring-boot:run "-Dspring-boot.run.jvmArguments=$env:JAVA_OPTS"

Pop-Location
