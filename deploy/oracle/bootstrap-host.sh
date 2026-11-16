#!/usr/bin/env bash
# Run only on the approved, fresh Ubuntu ARM64 VM as root. Never on the workstation.
set -euo pipefail
umask 077
[[ "$EUID" -eq 0 && "$(uname -m)" == aarch64 ]] || { echo 'A root shell on the Ubuntu ARM64 VM is required.' >&2; exit 1; }
source /etc/os-release
[[ "$ID" == ubuntu && "$VERSION_ID" == 24.04 ]] || { echo 'This bootstrap targets Ubuntu 24.04 LTS only.' >&2; exit 1; }
if command -v docker >/dev/null 2>&1; then
  docker info >/dev/null 2>&1 || { echo 'Existing Docker daemon is unavailable; inspect it before host changes.' >&2; exit 1; }
  if [[ -n "$(docker ps --all --quiet)" || -n "$(docker volume ls --quiet)" ]]; then
    echo 'Fresh-host bootstrap refuses to disrupt existing containers or data volumes. Review a maintenance plan first.' >&2; exit 1
  fi
fi
[[ "$#" -eq 2 ]] || { echo 'Usage: bootstrap-host.sh APPROVED_SSH_IPV4_CIDR DEPLOY_PUBLIC_KEY_FILE' >&2; exit 1; }
ssh_cidr="$1"; key_file=$(realpath -- "$2")
python3 - "$ssh_cidr" "${SSH_CONNECTION:-}" <<'PY'
import ipaddress,sys
network=ipaddress.ip_network(sys.argv[1],strict=False)
if network.version!=4 or network.prefixlen<24:raise SystemExit('Use an explicitly approved, narrow IPv4 source range.')
if sys.argv[2] and ipaddress.ip_address(sys.argv[2].split()[0]) not in network:raise SystemExit('Current SSH source is outside the approved range; preserve access.')
PY
[[ -f "$key_file" ]] && ssh-keygen -l -f "$key_file" >/dev/null
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get upgrade --with-new-pkgs --yes
apt-get install --yes ca-certificates curl gnupg python3 openssl unattended-upgrades iptables procps
install -m 0755 -d /etc/apt/keyrings
curl --fail --silent --show-error https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
fingerprint=$(gpg --show-keys --with-colons /etc/apt/keyrings/docker.asc | awk -F: '$1=="fpr" {print $10;exit}')
[[ "$fingerprint" == 9DC858229FC7DD38854AE2D88D81803C0EBFCD88 ]] || { echo 'Docker repository signing key changed; review before continuing.' >&2; exit 1; }
chmod a+r /etc/apt/keyrings/docker.asc
printf 'deb [arch=arm64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu noble stable\n' > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install --yes docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
install -d -m 0755 /etc/docker
python3 - <<'PY'
import json,pathlib
p=pathlib.Path('/etc/docker/daemon.json')
d=json.loads(p.read_text()) if p.exists() else {}
d.update({'log-driver':'json-file','log-opts':{'max-size':'10m','max-file':'3'},'live-restore':True})
p.write_text(json.dumps(d,indent=2)+'\n')
PY
systemctl enable --now docker
systemctl restart docker
if ! id supplyprint-deploy >/dev/null 2>&1; then useradd --create-home --shell /bin/bash supplyprint-deploy; fi
install -d -m 0700 -o supplyprint-deploy -g supplyprint-deploy /home/supplyprint-deploy/.ssh
install -m 0600 -o supplyprint-deploy -g supplyprint-deploy "$key_file" /home/supplyprint-deploy/.ssh/authorized_keys
# Deliberately do not add the deployment user to the root-equivalent docker group.
install -d -m 0755 /opt/supplyprint /usr/local/lib/supplyprint
install -m 0600 /dev/null /etc/sudoers.d/supplyprint-deploy
printf 'supplyprint-deploy ALL=(root) NOPASSWD: /usr/local/sbin/supplyprint-ops\n' > /etc/sudoers.d/supplyprint-deploy
visudo -cf /etc/sudoers.d/supplyprint-deploy
cat > /etc/ssh/sshd_config.d/00-supplyprint.conf <<'SSH'
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
PermitRootLogin prohibit-password
SSH
sshd -t
sshd -T | grep -x 'passwordauthentication no' >/dev/null
sshd -T | grep -x 'kbdinteractiveauthentication no' >/dev/null
systemctl reload ssh
cat > /etc/apt/apt.conf.d/52supplyprint-security <<'APT'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
Unattended-Upgrade::Automatic-Reboot "false";
APT
systemctl enable --now unattended-upgrades
printf '%s\n' "$ssh_cidr" > /etc/supplyprint-ssh-cidr
install -m 0755 "$(dirname -- "$0")/host-firewall.sh" /usr/local/sbin/supplyprint-firewall
install -m 0644 "$(dirname -- "$0")/systemd/supplyprint-firewall.service" /etc/systemd/system/supplyprint-firewall.service
systemctl daemon-reload
systemctl enable --now supplyprint-firewall
echo 'Host prepared. Keep this SSH session open and verify a SECOND key-authenticated session before closing it.'
[[ ! -f /var/run/reboot-required ]] || echo 'Security updates require a reboot. Schedule it after deployment and SSH recovery validation.'
