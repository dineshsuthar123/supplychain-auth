#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" -eq 0 ]] || exit 1
root=$(cd -- "$(dirname -- "$0")" && pwd -P)
[[ "$root" == /opt/supplyprint/deploy/oracle ]] || { echo 'Install the reviewed release under /opt/supplyprint first.' >&2; exit 1; }
chown -R root:root /opt/supplyprint/deploy
find "$root" -maxdepth 1 -name '*.sh' -exec chmod 0755 {} +
chmod 0600 "$root/.env"
cat > /usr/local/sbin/supplyprint-ops <<'SH'
#!/usr/bin/env bash
set -euo pipefail
unset ORACLE_ENV_FILE ORACLE_ROOT ORACLE_STATE_DIR ORACLE_BACKUP_DIR ORACLE_PROJECT_NAME
exec /opt/supplyprint/deploy/oracle/ops.sh "$@"
SH
chmod 0755 /usr/local/sbin/supplyprint-ops
for unit in supplyprint-monitor.service supplyprint-monitor.timer supplyprint-backup.service supplyprint-backup.timer; do
  install -m 0644 "$root/systemd/$unit" "/etc/systemd/system/$unit"
done
systemctl daemon-reload
systemctl enable --now supplyprint-monitor.timer supplyprint-backup.timer
