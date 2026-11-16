#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
mkdir -p -- "$ORACLE_BACKUP_DIR"
chmod 700 "$ORACLE_BACKUP_DIR"
[[ "${BACKUP_RETENTION_DAYS:-7}" =~ ^[1-9][0-9]?$ && "${BACKUP_MAX_MIB:-2048}" =~ ^[0-9]+$ ]] || exit 1
available=$(df -Pm "$ORACLE_BACKUP_DIR" | awk 'NR==2 {print $4}')
database_bytes=$(compose exec -T db psql -X -A -t -q -U postgres -d supplyprint -c "SELECT pg_database_size('supplyprint')")
[[ "$database_bytes" =~ ^[0-9]+$ ]] || exit 1
required=$(((database_bytes+1048575)/1048576+1024))
[[ "$available" -ge "$required" ]] || { echo 'Backup refused: insufficient conservative dump space plus 1 GiB reserve.' >&2; exit 1; }
target=$(mktemp "$ORACLE_BACKUP_DIR/supplyprint-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXX.dump")
trap '[[ -f "$target" && ! -s "$target" ]] && rm -- "$target" || true' EXIT
compose exec -T db pg_dump --username postgres --dbname supplyprint --format custom --no-owner --no-acl > "$target"
compose exec -T db pg_restore --list < "$target" > /dev/null
(cd -- "$ORACLE_BACKUP_DIR" && sha256sum "$(basename -- "$target")") > "$target.sha256"
# Remove only older backups in this known private directory, after a successful new dump.
find "$ORACLE_BACKUP_DIR" -maxdepth 1 -type f -name 'supplyprint-*.dump*' -mtime "+${BACKUP_RETENTION_DAYS:-7}" -delete
used=$(du -sm "$ORACLE_BACKUP_DIR" | cut -f1)
while [[ "$used" -gt "${BACKUP_MAX_MIB:-2048}" ]]; do
  oldest=$(find "$ORACLE_BACKUP_DIR" -maxdepth 1 -type f -name 'supplyprint-*.dump' ! -name "$(basename -- "$target")" -printf '%T@ %f\n' | sort -n | sed -n '1p' | cut -d' ' -f2-)
  [[ "$oldest" =~ ^supplyprint-[0-9TZ]+-[a-zA-Z0-9]+\.dump$ ]] || { echo 'WARNING: the new dump alone exceeds the backup budget. Preserve it and arrange an off-VM copy.' >&2; exit 1; }
  rm -- "$ORACLE_BACKUP_DIR/$oldest"
  [[ ! -f "$ORACLE_BACKUP_DIR/$oldest.sha256" ]] || rm -- "$ORACLE_BACKUP_DIR/$oldest.sha256"
  used=$(du -sm "$ORACLE_BACKUP_DIR" | cut -f1)
done
printf '%s\n' "$target"
