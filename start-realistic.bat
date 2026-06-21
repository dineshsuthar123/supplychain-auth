@echo off
REM =============================================================================
REM Start product-service with REALISTIC profile
REM Requires: PostgreSQL, Redis, Kafka running (docker-compose up -d)
REM =============================================================================

echo ============================================
echo   Starting REALISTIC mode
echo   PostgreSQL + Redis + Kafka (real stack)
echo ============================================
echo.

cd /d "%~dp0backend\product-service"

set SPRING_PROFILES_ACTIVE=realistic
set JAVA_OPTS=-Xmx1g -Xms512m -XX:+UseG1GC

echo Profiles: %SPRING_PROFILES_ACTIVE%
echo JVM: %JAVA_OPTS%
echo.

mvn spring-boot:run -Dspring-boot.run.jvmArguments="%JAVA_OPTS%"
