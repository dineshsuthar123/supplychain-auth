#!/bin/bash

# Supply Chain Performance Testing Suite
# Target: 5k+ verifications/minute with <400ms p95 latency

echo "🚀 Starting Supply Chain Authentication Performance Tests"
echo "=================================================="

# Create results directory
mkdir -p performance-results
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULTS_DIR="performance-results/test_${TIMESTAMP}"
mkdir -p $RESULTS_DIR

echo "📊 Test Results will be saved to: $RESULTS_DIR"

# Test 1: Baseline Performance (Low Load)
echo "🔍 Test 1: Baseline Performance (100 users, 5 minutes)"
locust -f locustfile.py \
    --headless \
    --users 100 \
    --spawn-rate 10 \
    --run-time 5m \
    --csv=$RESULTS_DIR/baseline \
    --html=$RESULTS_DIR/baseline_report.html

# Test 2: Target Load (5k requests/minute = ~83 RPS)
echo "⚡ Test 2: Target Throughput (500 users, 10 minutes)"
locust -f locustfile.py \
    --headless \
    --users 500 \
    --spawn-rate 50 \
    --run-time 10m \
    --csv=$RESULTS_DIR/target_load \
    --html=$RESULTS_DIR/target_load_report.html

# Test 3: High Load (Find breaking point)
echo "🔥 Test 3: Stress Test (1000 users, 15 minutes)"
locust -f locustfile.py \
    --headless \
    --users 1000 \
    --spawn-rate 100 \
    --run-time 15m \
    --csv=$RESULTS_DIR/stress_test \
    --html=$RESULTS_DIR/stress_test_report.html

# Test 4: Verification-Only Load (Simulate pure verification traffic)
echo "🎯 Test 4: Verification-Only Load (2000 users, 20 minutes)"
locust -f locustfile.py \
    --headless \
    --users 2000 \
    --spawn-rate 200 \
    --run-time 20m \
    --csv=$RESULTS_DIR/verification_only \
    --html=$RESULTS_DIR/verification_only_report.html \
    --user-classes HighVolumeVerificationUser

# Test 5: Sustained Load (Endurance Test)
echo "⏰ Test 5: Endurance Test (750 users, 30 minutes)"
locust -f locustfile.py \
    --headless \
    --users 750 \
    --spawn-rate 75 \
    --run-time 30m \
    --csv=$RESULTS_DIR/endurance \
    --html=$RESULTS_DIR/endurance_report.html

echo "✅ Performance Testing Complete!"
echo "=================================================="

# Generate summary report
cat > $RESULTS_DIR/test_summary.md << EOF
# 📈 Supply Chain Authentication Performance Test Results

**Test Date:** $(date)
**Target:** 5k+ verifications/minute with <400ms p95 latency

## Test Results Summary

### 🔍 Baseline Performance (100 users)
- Duration: 5 minutes
- Purpose: Establish performance baseline
- Results: See \`baseline_report.html\`

### ⚡ Target Throughput (500 users)
- Duration: 10 minutes  
- Target: ~83 RPS (5k/minute)
- Results: See \`target_load_report.html\`

### 🔥 Stress Test (1000 users)
- Duration: 15 minutes
- Purpose: Find system breaking point
- Results: See \`stress_test_report.html\`

### 🎯 Verification-Only Load (2000 users)
- Duration: 20 minutes
- Purpose: Pure verification throughput
- Results: See \`verification_only_report.html\`

### ⏰ Endurance Test (750 users)
- Duration: 30 minutes
- Purpose: Sustained load stability
- Results: See \`endurance_report.html\`

## Performance Metrics to Analyze

1. **Throughput**: Requests/second achieved
2. **Latency**: p50, p95, p99 response times
3. **Error Rate**: Failed requests percentage
4. **Resource Usage**: CPU/Memory consumption
5. **Auto-scaling**: HPA behavior under load

## Commands to Analyze Results

\`\`\`bash
# View detailed stats
cat ${RESULTS_DIR}/target_load_stats.csv

# Check error rates
grep "GET /api/verify" ${RESULTS_DIR}/target_load_failures.csv

# Monitor Kubernetes during tests
kubectl top pods -n supplychain-auth
kubectl get hpa -n supplychain-auth -w
\`\`\`
EOF

echo "📋 Test summary saved to: $RESULTS_DIR/test_summary.md"
echo "🌐 Open HTML reports in browser for detailed analysis"
