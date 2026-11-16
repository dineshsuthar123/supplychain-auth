#!/usr/bin/env bash
set -euo pipefail
[[ "$EUID" -eq 0 ]] || exit 1
ssh_cidr=$(cat /etc/supplyprint-ssh-cidr)
interface=$(ip -4 route show default | awk 'NR==1 {print $5}')
[[ -n "$interface" ]] || { echo 'No default IPv4 interface; inspect networking before changing the firewall.' >&2; exit 1; }
iptables -N SP-INPUT 2>/dev/null || true
iptables -F SP-INPUT
iptables -A SP-INPUT -i lo -j ACCEPT
iptables -A SP-INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
# Preserve DHCP replies on the primary interface, including OCI's link-local service.
iptables -A SP-INPUT -i "$interface" -p udp --sport 67 --dport 68 -j ACCEPT
iptables -A SP-INPUT -p tcp -s "$ssh_cidr" --dport 22 -j ACCEPT
iptables -A SP-INPUT -p tcp -m multiport --dports 80,443 -j ACCEPT
iptables -A SP-INPUT -p icmp -m limit --limit 10/second -j ACCEPT
iptables -A SP-INPUT -j DROP
iptables -C INPUT -j SP-INPUT 2>/dev/null || iptables -I INPUT 1 -j SP-INPUT
# Docker-published ports bypass INPUT/UFW. Filter original destination ports in DOCKER-USER too.
iptables -N SP-DOCKER 2>/dev/null || true
iptables -F SP-DOCKER
iptables -A SP-DOCKER -m conntrack --ctstate ESTABLISHED,RELATED -j RETURN
iptables -A SP-DOCKER -i "$interface" -p tcp -m conntrack --ctorigdstport 80 -j RETURN
iptables -A SP-DOCKER -i "$interface" -p tcp -m conntrack --ctorigdstport 443 -j RETURN
iptables -A SP-DOCKER -i "$interface" -j DROP
iptables -A SP-DOCKER -j RETURN
iptables -C DOCKER-USER -j SP-DOCKER 2>/dev/null || iptables -I DOCKER-USER 1 -j SP-DOCKER
# IPv6 is not provisioned for this VCN. Deny new IPv6 ingress rather than leaving an unfiltered path.
ip6tables -N SP6-INPUT 2>/dev/null || true
ip6tables -F SP6-INPUT
ip6tables -A SP6-INPUT -i lo -j ACCEPT
ip6tables -A SP6-INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
ip6tables -A SP6-INPUT -p ipv6-icmp -j ACCEPT
ip6tables -A SP6-INPUT -j DROP
ip6tables -C INPUT -j SP6-INPUT 2>/dev/null || ip6tables -I INPUT 1 -j SP6-INPUT
