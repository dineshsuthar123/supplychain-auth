# A1 capacity checks and smaller-VM review

The approved cloud launch remains **2 OCPUs / 12 GB RAM / 50 GB boot**, using the existing Hyderabad network. Neither the checker nor the optional review file changes this approval. `retry-launch` still rejects any changed allocation and only accepts confirmed availability of **2/12**. It retains all tenancy, quota, free-eligibility, saved-network and SSH checks.

## Current observation — 17 November 2026, 22:20 IST

The existing read-only `capacity` command was run first, from commit `5299b3ad`. It reported `OUT_OF_HOST_CAPACITY` for 2/12 and 1/6. The extended check, executed successfully through Windows Task Scheduler, also found **2/12, 2/8, 2/6 and 1/6 all unavailable**. Capacity changes dynamically; read the timestamp in the private latest result rather than treating this observation as a reservation. No networking was recreated and no VM, disk, paid resource or account upgrade was created.

## Resource review

The application remains the real Java ONNX Runtime 1.17.3 service with the committed model, PostgreSQL/pgvector, Flyway and named persistent volumes. Development embeddings are prohibited. Memory limits total **2,496 MiB / 2.4375 GiB**: API 1,536 MiB, PostgreSQL 768 MiB, Caddy 192 MiB. Reserving **2 GiB for Ubuntu/Docker and 1 GiB spare** produces a conservative **5.4375-GiB** planning requirement. This is a sizing estimate, not a complete-VM measurement.

| A1 configuration | Assessment | Required before changing the approved plan |
|---|---|---|
| 2 OCPU / 12 GB | Approved target; native ARM64 application acceptance already passed | Existing guarded retry, then actual VM acceptance |
| 2 OCPU / 8 GB | Strong smaller-memory candidate for one light SupplyPrint deployment; same CPU and container limits | Explicit approval of RAM change, then 8-GB VM memory/reboot/restore validation |
| 2 OCPU / 6 GB | Fits the estimated one-app budget, with less spare room | Explicit approval; monitor RSS, page cache, OOM events, disk and response time on the actual VM |
| 1 OCPU / 6 GB | Conditional light-demo candidate; lower CPU quotas and single-thread inference, with slower starts/requests | Explicit approval, candidate profile and actual 1-CPU/6-GB VM acceptance; no throughput guarantee |
| 4 GB or less | Not recommended within the conservative budget | Excluded from notifications and provisioning |

The optional **local/CI-only** `compose.review-1cpu.yml` caps API/DB/proxy at **0.65 / 0.20 / 0.10 CPU**, totaling 0.95, and sets ONNX inter/intra-op threads to one. The model, preprocessing, 128 dimensions, permissions, memory caps, database volume and API remain unchanged. The API receives 300 seconds of startup grace; its health check still requires real model/database readiness. ARM emulation initially exceeded the original 150-second startup allowance; the API subsequently became healthy. This is a latency tradeoff, not an embedding fallback.

The disposable review executes actual HTTPS auth, image enrollment/verification, two-tenant isolation, mapped ARM64 JNI libraries, 128-dimensional normalized vectors, database/API restart persistence and an isolated backup restore:

```powershell
python deploy/oracle/validate-local.py --start --small-resource-review --skip-rollback-test
```

It only targets `sp-oracle-check` on localhost. Results are generated privately at `reports/oracle/small-resource-validation.json`; native ARM64 CI also runs this review after the approved-profile acceptance. Docker CPU/memory caps do **not** simulate a complete Ubuntu VM with 6 GB RAM. Candidate classification therefore remains conditional until real cloud validation. Application-image rollback is tested separately under the approved profile. These smaller sizes do not carry the 12-GB plan's five-additional-project capacity assumptions.

## Optional Windows Task Scheduler notification

The installed task is **`SupplyPrint-Oracle-A1-Capacity`**. It runs every **three hours**, beginning **18 November 2026 at 01:19 IST**, under the current user's **Interactive / Limited** principal. A real manual task run completed with exit code 0 and `NO_CAPACITY`; the notification API was tested with a message explicitly labeled as a test. On-screen delivery is subject to Windows notification/Do Not Disturb settings.

The task uses the local OCI profile through the existing private SDK environment. Its fixed action runs `capacity-watch.py`, which invokes only `oci-infra.py capacity`. It has no provisioning mode, SSH operation, account upgrade, password storage, email recipient or automatic alternate-size selection. A smaller-candidate alert explicitly requires approval and VM validation. A fresh success is required; errors never reuse a previous `AVAILABLE` file. New availability notifies immediately, unchanged availability reminds at most daily, and no-capacity checks remain quiet.

Private, bounded files live under **`%LOCALAPPDATA%\SupplyPrint\OracleCapacity\`**, with access restricted to the current user and SYSTEM:

- `monitor-state.json`: `NO_CAPACITY`, `AVAILABLE` or `CHECK_FAILED` and the latest check time.
- `latest-capacity.json`: sanitized sizes/statuses; no keys or account/resource IDs.
- `last-alert.json`: persistent latest alert if Windows suppresses its balloon.
- `last-check.log`: bounded latest diagnostic output; errors may contain private account metadata.

The PC must be powered on, connected and the user logged in. Locking the screen keeps the session logged in; signing out prevents interactive runs. The task does not wake a sleeping PC; Windows is configured to run missed checks when available. Keep this worktree and `reports/oracle/oci-venv` in place. No checker runs on the OCI VM or generates load to avoid reclamation.

From the Oracle worktree:

```powershell
& deploy/oracle/capacity-task.ps1 -Action Status
& deploy/oracle/capacity-task.ps1 -Action Run
& deploy/oracle/capacity-task.ps1 -Action Disable
& deploy/oracle/capacity-task.ps1 -Action Enable
& deploy/oracle/capacity-task.ps1 -Action Remove
# Opt in again after removal:
& deploy/oracle/capacity-task.ps1 -Action Install
# Explicit local notification test; no capacity claim or cloud calls:
& reports/oracle/oci-venv/Scripts/python.exe deploy/oracle/capacity-watch.py --test-notification
```

Removal unregisters only this user's matching task; it retains cloud resources, credentials and private result files. The task can be disabled at any time. It does not start an agent or a deployment when availability changes: resume the separate guarded retry workflow while the slot remains available. Smaller-only availability is a choice to review, not approval to change 2/12.

Microsoft documents [repeating task triggers](https://learn.microsoft.com/en-us/powershell/module/scheduledtasks/new-scheduledtasktrigger?view=windowsserver2025-ps) and [notification balloons](https://learn.microsoft.com/en-us/dotnet/api/system.windows.forms.notifyicon.showballoontip?view=netframework-4.8.1). Oracle documents [host-capacity shortages](https://docs.oracle.com/en-us/iaas/Content/Compute/Tasks/troubleshooting-out-of-host-capacity.htm) and [Always Free limits](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm).
