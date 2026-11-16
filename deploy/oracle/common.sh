#!/usr/bin/env bash
set -euo pipefail
umask 077
ORACLE_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ORACLE_ENV_FILE="${ORACLE_ENV_FILE:-$ORACLE_ROOT/.env}"
ORACLE_STATE_DIR="${ORACLE_STATE_DIR:-$ORACLE_ROOT/state}"
ORACLE_BACKUP_DIR="${ORACLE_BACKUP_DIR:-$ORACLE_ROOT/backups}"
[[ -f "$ORACLE_ENV_FILE" ]] || { echo 'Generate the private environment file first.' >&2; exit 1; }
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ -z "$line" || "$line" == \#* ]] && continue
  [[ "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]] || { echo 'Invalid environment line' >&2; exit 1; }
  export "${BASH_REMATCH[1]}=${BASH_REMATCH[2]}"
done < "$ORACLE_ENV_FILE"
for key in DB_ADMIN_PASSWORD DB_APP_PASSWORD DB_MIGRATION_PASSWORD JWT_SECRET; do
  value="${!key:-}"
  [[ "$value" =~ ^[A-Za-z0-9_-]{43,128}$ && "$value" != *replace* && "$value" != *changeme* ]] || { echo "Generate a strong URL-safe value for $key before starting any service." >&2; exit 1; }
done
[[ "$DB_ADMIN_PASSWORD" != "$DB_APP_PASSWORD" && "$DB_ADMIN_PASSWORD" != "$DB_MIGRATION_PASSWORD" && "$DB_APP_PASSWORD" != "$DB_MIGRATION_PASSWORD" ]] || { echo 'Database credentials must be independent.' >&2; exit 1; }
compose() { docker compose --project-name "${ORACLE_PROJECT_NAME:-supplyprint-oracle}" --env-file "$ORACLE_ENV_FILE" --file "$ORACLE_ROOT/compose.yml" "$@"; }
wait_ready() {
  local attempts=0
  until curl --fail --silent --show-error --max-time 5 "http://127.0.0.1:${API_LOCAL_PORT:-18081}/actuator/health/readiness" >/dev/null 2>&1; do
    attempts=$((attempts+1)); [[ "$attempts" -lt "${ORACLE_READY_ATTEMPTS:-90}" ]] || return 1
    sleep 2
  done
}
