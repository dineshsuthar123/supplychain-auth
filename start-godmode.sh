#!/bin/bash
# =========================================================================
# 🔥 TIER-4 GODLY SERVER STARTUP SCRIPT 🔥
# =========================================================================
# 
# This script starts the Spring Boot server with optimal JVM settings
# for achieving Tier-4 Godly performance metrics.
#
# Target:
# - RPS: 50,000 - 100,000+
# - p95: ≤ 20ms
# - p99: ≤ 30ms
# - p99.9: ≤ 60ms
# =========================================================================

cd "$(dirname "$0")/backend/product-service"

echo "🔥 TIER-4 GODLY SERVER STARTUP 🔥"
echo "================================="
echo ""

# JVM Settings for Maximum Performance
# ------------------------------------
# -Xmx2g -Xms2g     : Fixed heap (2GB) - eliminates heap resizing
# -XX:+UseZGC       : Z Garbage Collector - sub-10ms GC pauses
# -XX:MaxGCPauseMillis=10 : Target max GC pause
# -XX:+AlwaysPreTouch : Pre-touch memory pages at startup
# -XX:ConcGCThreads=4 : Concurrent GC threads
# -XX:ParallelGCThreads=8 : Parallel GC threads
# -XX:+UseStringDeduplication : Reduce string memory
# -XX:-UseBiasedLocking : Disable biased locking (deprecated in 17)
# -Dspring.profiles.active=godmode : Use godmode profile

export JAVA_OPTS="-Xmx2g -Xms2g \
  -XX:+UseZGC \
  -XX:MaxGCPauseMillis=10 \
  -XX:+AlwaysPreTouch \
  -XX:ConcGCThreads=4 \
  -XX:ParallelGCThreads=8 \
  -XX:+UseStringDeduplication \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=godmode"

echo "JVM Settings:"
echo "  Heap: 2GB fixed"
echo "  GC: ZGC (sub-10ms pauses)"
echo "  Profile: godmode"
echo ""
echo "Starting server..."
echo ""

mvn spring-boot:run -Dspring-boot.run.jvmArguments="$JAVA_OPTS" -q
