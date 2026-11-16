#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
[[ "$#" -eq 1 && -f "$1" ]] || { echo 'Usage: restore-check.sh BACKUP.dump' >&2; exit 1; }
backup=$(realpath -- "$1")
[[ "$(dirname -- "$backup")" == "$(realpath -- "$ORACLE_BACKUP_DIR")" && "$(basename -- "$backup")" =~ ^supplyprint-[0-9TZ]+-[a-zA-Z0-9]+\.dump$ ]] || { echo 'Use a root-controlled backup from this deployment.' >&2; exit 1; }
[[ -f "$backup.sha256" ]] && (cd -- "$(dirname -- "$backup")" && sha256sum --check "$backup.sha256")
database="sp_restore_$(date -u +%Y%m%d%H%M%S)_$RANDOM"
[[ "$database" =~ ^sp_restore_[0-9_]+$ ]] || exit 1
cleanup() { compose exec -T db dropdb --username postgres --if-exists "$database" >/dev/null; }
trap cleanup EXIT
compose exec -T db createdb --username postgres --owner supplyprint_migration "$database"
compose exec -T db pg_restore --exit-on-error --clean --if-exists --username postgres --dbname "$database" --no-owner --no-acl < "$backup"
compose exec -T db psql --username postgres --dbname "$database" --no-psqlrc --set ON_ERROR_STOP=1 <<'SQL'
SELECT count(*) AS successful_migrations FROM flyway_schema_history WHERE success;
SELECT extversion AS vector_version FROM pg_extension WHERE extname='vector';
SELECT count(*) AS fingerprints, coalesce(bool_and(vector_dims(embedding)=128),true) AS correct_dimensions FROM product_fingerprints;
SELECT count(*) AS verification_events FROM verification_events;
DO $$ BEGIN
 IF (SELECT count(*) FROM flyway_schema_history WHERE success) < 8 THEN RAISE EXCEPTION 'Migration history incomplete'; END IF;
 IF EXISTS(SELECT 1 FROM product_fingerprints WHERE vector_dims(embedding)<>128) THEN RAISE EXCEPTION 'Invalid restored vector'; END IF;
END $$;
SQL
echo 'Restore checked in an isolated temporary database. Live database was not overwritten.'
