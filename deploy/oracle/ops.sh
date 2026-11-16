#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/common.sh"
mkdir -p -- "$ORACLE_STATE_DIR"
action="${1:-status}"
shift || true
case "$action" in
  deploy)
    [[ "$#" -eq 0 ]] || exit 1
    compose config --quiet
    [[ "$(docker image inspect --format '{{.Architecture}}' "$APP_IMAGE")" == arm64 ]] || { echo 'An actual ARM64 image is required.' >&2; exit 1; }
    compose up --detach db api
    if ! wait_ready; then echo 'API failed readiness. Proxy was not started; database volume retained.' >&2; exit 1; fi
    compose up --detach proxy
    ;;
  update)
    [[ "$#" -eq 1 && "$1" =~ ^sha256:[a-f0-9]{64}$ ]] || { echo 'Use a loaded immutable sha256 image ID.' >&2; exit 1; }
    [[ "$(docker image inspect --format '{{.Architecture}}' "$1")" == arm64 ]] || exit 1
    "$ORACLE_ROOT/backup.sh"
    previous="$APP_IMAGE"
    printf '%s\n' "$previous" > "$ORACLE_STATE_DIR/previous-image"
    export APP_IMAGE="$1"
    compose up --detach --no-deps api
    if ! wait_ready; then
      export APP_IMAGE="$previous"
      compose up --detach --no-deps api
      wait_ready || true
      echo 'Application update failed; previous image restored. Database migrations were NOT reversed.' >&2
      exit 1
    fi
    python3 - "$ORACLE_ENV_FILE" "$APP_IMAGE" <<'PY'
import os,pathlib,sys
p=pathlib.Path(sys.argv[1]);s=p.read_text();lines=s.splitlines()
p.write_text('\n'.join('APP_IMAGE='+sys.argv[2] if x.startswith('APP_IMAGE=') else x for x in lines)+'\n');os.chmod(p,0o600)
PY
    ;;
  rollback)
    [[ -f "$ORACLE_STATE_DIR/previous-image" ]] || exit 1
    previous=$(cat "$ORACLE_STATE_DIR/previous-image")
    [[ "$previous" =~ ^(sha256:[a-f0-9]{64}|[a-zA-Z0-9/._:-]+)$ ]] || exit 1
    APP_IMAGE="$previous" compose up --detach --no-deps api
    wait_ready || { echo 'Rollback incompatible with schema; restore into a separate database for investigation.' >&2; exit 1; }
    python3 - "$ORACLE_ENV_FILE" "$previous" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1]);p.write_text('\n'.join('APP_IMAGE='+sys.argv[2] if x.startswith('APP_IMAGE=') else x for x in p.read_text().splitlines())+'\n')
PY
    ;;
  restart) compose restart api; wait_ready ;;
  stop) compose stop ;;
  status) compose ps ;;
  image-id)
    [[ "$#" -eq 1 && "$1" =~ ^supplyprint-oracle:[a-f0-9]{40}$ ]] || exit 1
    docker image inspect --format '{{.Id}}' "$1"
    ;;
  logs) compose logs --tail 100 api ;;
  health) wait_ready; echo 'API and database readiness UP.' ;;
  backup) exec "$ORACLE_ROOT/backup.sh" ;;
  grant-role) exec "$ORACLE_ROOT/grant-role.sh" "$@" ;;
  backup-path) "$ORACLE_ROOT/backup.sh" >/dev/null; find "$ORACLE_BACKUP_DIR" -maxdepth 1 -type f -name 'supplyprint-*.dump' -printf '%T@ %p\n' | sort -nr | sed -n '1p' | cut -d' ' -f2- ;;
  read-backup)
    [[ "$#" -eq 1 && "$1" =~ ^supplyprint-[0-9TZ]+-[a-zA-Z0-9]+\.dump(\.sha256)?$ ]] || exit 1
    cat -- "$ORACLE_BACKUP_DIR/$1"
    ;;
  restore-check) exec "$ORACLE_ROOT/restore-check.sh" "$@" ;;
  monitor) exec "$ORACLE_ROOT/monitor.sh" ;;
  load-image)
    [[ "$#" -eq 0 ]] || exit 1
    [[ "$(df -Pm /var/lib/docker | awk 'NR==2 {print $4}')" -ge 4096 ]] || { echo 'At least 4 GiB free disk is required.' >&2; exit 1; }
    timeout 180 bash -o pipefail -c 'gzip -dc | head -c 1200000000 | docker image load'
    ;;
  *) echo 'Commands: deploy update rollback restart stop status logs health backup restore-check monitor load-image' >&2; exit 1 ;;
esac
