# SupplyPrint on Oracle Always Free

This deployment targets **SupplyPrint from main**, not the separate Lotline application. The frontend is unchanged. Start with [READINESS.md](READINESS.md) and [VALIDATION.md](VALIDATION.md). **Repository configuration and local tests are not an OCI deployment.**

## Scope and cost gate

Planned infrastructure is one **VM.Standard.A1.Flex, 2 OCPUs, 12 GB RAM, Ubuntu 24.04 ARM64, 50 GB boot storage**, in **ap-hyderabad-1 only when it is the tenancy home region**. Existing A1 allocations and every home-region boot/block volume count against the 2/12/200 limits. There is no paid fallback, account upgrade, NAT gateway, load balancer, Kubernetes or managed database.

`oci-infra.py inspect` inventories all accessible compartments and fails closed when permissions, quota, capacity or eligibility cannot be verified. `provision` repeats that inventory and also requires current, tenancy-matched Console evidence of a Free Tier account and the Always Free label. API shape billing must be ALWAYS_FREE or LIMITED_FREE; PAID/unknown is rejected. Safety checks remain active under `python -O`.

The script records each created resource in private `state/provisioned.json`. A failed launch never deletes resources or volumes automatically. If a launch returns out-of-capacity, review the saved state and wait for capacity; do not upgrade or duplicate the VM.

Authoritative limits and idle-reclamation policy: [Oracle Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm). Availability and remaining free capacity must be checked in the actual tenancy. OCI can reclaim an idle VM; monitoring must not generate synthetic workload to evade this policy.

## Access and first setup

1. Configure a local OCI SDK/API-key profile at `~/.oci/config`, with its key outside Git. Security-token profiles are also supported. Do not paste keys into chat or command history.

   On this Windows workstation, use Oracle Console **Profile → User settings → API Keys (or Tokens and keys) → Add API Key**. Download the private key into `C:\Users\Naresh Suthar\.oci\`, select Add, and save the generated Configuration File Preview as `C:\Users\Naresh Suthar\.oci\config` (no `.txt` extension). Set `key_file` to the downloaded PEM's actual local path and retain the profile header. Preserve any existing profiles. Follow Oracle's [API-key instructions](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm) and [Windows configuration location](https://docs.oracle.com/en-us/iaas/Content/API/Concepts/sdkconfig.htm). Limit private-key file access to your user and SYSTEM. These are API credentials, distinct from the already generated SSH keys.

2. Install the isolated SDK and inspect the account:

   ```sh
   python3 -m venv .venv-oci
   .venv-oci/bin/pip install -r deploy/oracle/requirements-oci.txt
   .venv-oci/bin/python deploy/oracle/oci-infra.py inspect
   ```

3. Verify the Billing account type and selected image/shape Always Free labels in the Console. Save the factual check in ignored `deploy/oracle/state/console-evidence.json`, following [the example](console-evidence.example.json). Never set it to verified without checking. IAM does not return the account's commercial plan type.
4. Generate independent administrator and deployment SSH keys with `python3 deploy/oracle/prepare-access.py`, or use two existing approved keys. Only public halves are uploaded. Private keys stay in ignored `state/` with restricted permissions. Use the operator key for the initial Ubuntu administrator and the deployment key only for `supplyprint-deploy`. Supply the explicitly approved source IPv4 CIDR.
5. Create the eligible VM:

   ```sh
   .venv-oci/bin/python deploy/oracle/oci-infra.py provision \
     --console-evidence deploy/oracle/state/console-evidence.json \
     --ssh-public-key deploy/oracle/state/operator_ed25519.pub \
     --ssh-cidr YOUR_APPROVED_PUBLIC_IP/32
   ```

   If an eligible A1 VM already exists, first use `inspect --reuse-instance INSTANCE_OCID` instead. This verifies total usage without adding another allocation; inspect its workloads and SSH access before planning any host changes. The fresh-host bootstrap intentionally refuses existing containers/data volumes or an unavailable existing Docker daemon.

6. Confirm the SSH host-key fingerprint through the OCI Console connection before adding it to a private `known_hosts`. Keep StrictHostKeyChecking enabled. Connect as Ubuntu's `ubuntu` administrator. Copy the reviewed branch to `/opt/supplyprint` and the deployment public key to the VM.
7. Run `sudo env SSH_CONNECTION="$SSH_CONNECTION" bash /opt/supplyprint/deploy/oracle/bootstrap-host.sh APPROVED_IP/32 DEPLOYMENT_PUBLIC_KEY_FILE`. Use `deployment_ed25519.pub` here, not the administrator key. It installs signed Docker packages and security updates, disables password login, and applies host and Docker ingress filters. It targets a fresh Ubuntu ARM64 VM; existing hosts need a separate reviewed maintenance plan. **Keep the original SSH session open and verify a second key-based login.**
8. Generate `/opt/supplyprint/deploy/oracle/.env` on the VM, using `generate-env.py`. This refuses to replace an existing file and creates independent random administrator, runtime, migration and JWT secrets. Use the exact image tag `supplyprint-oracle:FULL_GIT_SHA`, your API hostname, certificate-contact email and HTTPS frontend origin.
9. Run `sudo bash /opt/supplyprint/deploy/oracle/install-operations.sh`. The deployment account gets only the root-owned operations wrapper, not the root-equivalent Docker group. Keep the release scripts and `.env` owned by root. Copy only reviewed deployment files; do not transfer workstation credentials, reports or datasets.

## Deploy, update and rollback

The public repository is checked on the standard native `ubuntu-24.04-arm` GitHub runner. Its workflow runs the actual model/database tests, builds ARM64 and exercises the disposable stack. Standard runners are [free for public repositories](https://docs.github.com/en/actions/reference/runners/github-hosted-runners); the job is disabled for private repositories instead of risking paid minutes. It stores no large image artifacts or registry packages.

From the approved workstation IP:

```sh
python deploy/oracle/deploy-from-workstation.py --host VM_IP \
  --key PRIVATE_SSH_KEY --known-hosts VERIFIED_KNOWN_HOSTS --initial
# Subsequent reviewed commits: omit --initial.
```

The helper requires the exact committed source to pass `oracle-arm64`, builds only when that SHA image is absent locally, streams the image over SSH and updates by the loaded immutable image ID. This avoids widening port 22 to changing hosted-runner addresses, using paid static-IP runners, or paying for image artifact storage. A restart does not rebuild the backend.

The initial administrator must set the `.env` image tag to that first deployed SHA. Later `update` commands back up first, switch only the API, and restore the previous image if readiness fails. Database migrations are **never reversed automatically**. Additive/backward-compatible migrations are required for automatic application rollback; incompatible changes need a separately reviewed recovery plan.

Commands after SSH login as `supplyprint-deploy`:

| Operation | Command |
|---|---|
| Initial start | `sudo supplyprint-ops deploy` |
| Update | `sudo supplyprint-ops update sha256:LOADED_IMAGE_ID` |
| Restart API | `sudo supplyprint-ops restart` |
| Stop without deleting data | `sudo supplyprint-ops stop` |
| Status / logs / readiness | `sudo supplyprint-ops status` / `logs` / `health` |
| Backup | `sudo supplyprint-ops backup` |
| Reviewed role grant after backup | `sudo supplyprint-ops grant-role EMAIL MANUFACTURER` |
| Isolated restore exercise | `sudo supplyprint-ops restore-check /opt/supplyprint/deploy/oracle/backups/FILE.dump` |
| Previous application image | `sudo supplyprint-ops rollback` |
| Private resource check | `sudo supplyprint-ops monitor` |

Never use `docker compose down --volumes`, volume prune, or automated database replacement. Stop/recreate/update operations retain the named PostgreSQL volume.

## HTTPS and frontend configuration

Create an **A record** for your chosen API subdomain pointing to the VM public IPv4. Do not publish AAAA without implementing the matching IPv6 network policy. Caddy obtains and renews a public certificate when the DNS and ports 80/443 work; it redirects HTTP to HTTPS. Login credentials and JWTs must use HTTPS.

Without DNS, keep the proxy loopback-bound (`API_HOST=localhost`, `PROXY_BIND_IP=127.0.0.1`, HTTP/HTTPS ports 18080/18443), use Caddy's local test CA and a secure SSH tunnel:

```sh
ssh -N -L 18443:127.0.0.1:18443 -i KEY -o StrictHostKeyChecking=yes ubuntu@VM_IP
```

Retrieve the public local-CA certificate over the authenticated SSH session, and pass it as `--ca` to verification. Do not disable certificate verification or trust this test CA globally. This is secure-tunnel validation, **not public HTTPS certification**. For actual public DNS, change proxy ports to 80/443, bind publicly, and validate the public certificate separately.

The unchanged SupplyPrint frontend must use its actual API configuration, `credentials: include` for login/refresh/logout, and the exact trusted HTTPS origin. Prefer same-origin frontend rewrites for `/auth/*` and `/api/*` to `https://API_HOST` with paths preserved. This keeps the host-only HttpOnly Secure SameSite=Lax refresh cookie on the frontend origin; direct cross-site API cookies may be blocked by browser policy. No signing, database or SSH secrets belong in React environment variables. Do not point the unrelated deployed Lotline frontend at these different SupplyPrint routes.

The current SupplyPrint SPA reads `REACT_APP_API_BASE`, stores access tokens in localStorage, and has client-only logout with no automatic refresh. These are recorded frontend limitations, not fixed or deployed by this backend-only change. Use the verified API client path until frontend session handling and same-origin routing are separately completed; no whole-application/browser readiness claim is made here.

Public proxy routes are the requested auth, image enrollment/verification, evidence, dashboard and minimal `/actuator/health`. Swagger, metrics, SIWE, raw client embeddings and legacy bypass routes are blocked by both proxy and the Oracle application policy. Only the proxy publishes public ports. PostgreSQL has no host port; API readiness is bound to host loopback.

Host rules preserve DHCP replies and the existing Oracle image's outbound iSCSI/service rules. Do not flush those provider rules or enable UFW blindly: Oracle documents [DHCP and boot-volume availability risks](https://docs.oracle.com/en-us/iaas/Content/Compute/References/bestpracticescompute.htm). Inspect lease renewal, the effective rules and second-session SSH before testing a reboot.

## Database permissions, operator setup and security

PostgreSQL 16 / pgvector is self-hosted. Initial setup creates `supplyprint_migration` as schema owner and `supplyprint_app` with only application DML, no superuser/role/database/schema creation and no migration-history writes. The database administrator password is not supplied to the API. Flyway performs the unchanged eight migrations with its separate owner; checksums are never baselined around errors. Pool size is five.

Public registration always creates an unprivileged USER in a new tenant. An administrator must independently review and grant a needed manufacturer/verifier/auditor role before image operations. Never enable self-service privileged-role assignment. Role changes require a new sign-in to refresh JWT claims. Existing pre-cutover refresh cookies require fresh login: refresh values now use SHA-256 prehashing before BCrypt, avoiding the previous 72-byte truncation/rejection issue.

The Oracle profile rejects development profiles, weak secrets, insecure cookies, wildcard/non-HTTPS origins, blockchain enablement and unsafe self-service roles. It uses real ONNX only. The public API has bounded upload size, dimension checks before image decode, two simultaneous inference slots and per-IP route limits including auth. Caddy discards untrusted forwarding headers and sets security/no-store headers. Application root filesystem is read-only; process UID is 10001 with no capabilities. Logs and resources are bounded.

## Backups and recovery

Daily custom-format dumps have private permissions, checksum sidecars, up-to-seven-day age retention and a 2-GiB local budget. Older copies are pruned only after a valid new dump completes. The space check conservatively reserves the current database size plus 1 GiB; a new dump that alone exceeds the budget is retained with a visible failure, requiring an off-VM copy. Logs and systemd monitor output are private; inspect `journalctl -u supplyprint-backup` and `-u supplyprint-monitor` for failures/warnings.

`restore-check` accepts only root-controlled deployment backups, restores into a newly generated `sp_restore_*` database, checks migrations/extension/vector dimensions, and removes that disposable test database. It never restores over `supplyprint`.

For a no-additional-service-cost off-VM copy, use the workstation helper's `--backup-download PRIVATE_LOCAL_DIRECTORY`, retaining both `.dump` and `.sha256` privately. Keep that copy on a different device/location with its own retention policy; test an independent restore. **Local-only dumps are not disaster recovery.** OCI Object Storage is an optional future destination only after checking actual remaining free storage and request limits; no bucket or paid storage was created here.

After VM loss/reclamation: inspect tenancy usage, preserve surviving boot volumes, recover a reviewed release and private secrets, restore the selected dump into a separate database, verify vector/image/auth/tenant behavior, then explicitly approve a connection/DNS cutover. Do not duplicate allocations beyond the free ceiling or delete the old volume before validation.

## Five additional backends

Use the same `oracle-edge` proxy network with unique service aliases (`project2-api`, etc.) and separate subdomains/site blocks. SupplyPrint's upstream alias is `supplyprint-api` to avoid service-name collisions. Share the existing PostgreSQL process through the private `oracle-database` network, but create a distinct database, migration owner and runtime role for every project; revoke PUBLIC database access. Do not share credentials or tenant tables.

For five lightweight additions, budget about **768 MiB–1 GiB RAM and 0.1–0.125 CPU each**, retain about 2 GiB for Ubuntu/Docker, and leave at least 1 GiB memory headroom. Before expansion, lower per-app pools to three (18 total across six apps), reserve migration/admin slots, and review database memory. The initial stack caps API at 1.5 GiB / 1 CPU, PostgreSQL at 768 MiB / 0.5 CPU and proxy at 192 MiB / 0.15 CPU; reduce CPU caps or accept controlled time sharing when adding projects.

Two physical OCPUs cannot provide six simultaneous heavy inference workloads. Move or pause projects when real response-time, CPU, memory, connection, disk or inode warnings persist. Expand boot storage only within verified remaining free allocation; never silently upgrade compute or account type. This is a capacity plan, not a measured six-project throughput guarantee.
