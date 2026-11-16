#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
warnings=0
date -u +%FT%TZ
free -m
df -h / /var/lib/docker
df -i / /var/lib/docker
for used in $(df -P / /var/lib/docker | awk 'NR>1 {gsub("%","",$5);print $5}') $(df -Pi / /var/lib/docker | awk 'NR>1 {gsub("%","",$5);print $5}'); do
  if [[ "$used" -ge 80 ]]; then echo 'WARNING: disk or inode usage at least 80%'; warnings=1; fi
done
memory=$(awk '/MemTotal/{t=$2}/MemAvailable/{a=$2}END{printf "%.0f",100*(t-a)/t}' /proc/meminfo)
if [[ "$memory" -ge 85 ]]; then echo 'WARNING: host memory usage at least 85%'; warnings=1; fi
uptime
cpu=$(vmstat 1 2 | tail -1 | awk '{print 100-$15}')
printf 'Sample CPU utilization: %s%%\n' "$cpu"
if [[ "$cpu" -ge 85 ]]; then echo 'WARNING: sampled CPU utilization at least 85%'; warnings=1; fi
compose ps
compose exec -T db pg_isready -U postgres -d supplyprint || warnings=1
curl --fail --silent --show-error --max-time 5 -w ' API response seconds=%{time_total}\n' "http://127.0.0.1:${API_LOCAL_PORT:-18081}/actuator/health/readiness" || warnings=1
compose logs --since 5m --no-color api | grep -Ei 'ERROR|OutOfMemory|FATAL' && warnings=1 || true
exit "$warnings"
