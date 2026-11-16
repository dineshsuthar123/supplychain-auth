# Oracle deployment validation

## Actual status

**OCI deployment: blocked by Oracle host capacity, checked 17 November 2026 IST.** Authentication works after correcting Windows' hidden `config.txt` extension and restricting configuration/key access. The owner confirmed Free Tier, not PAYG; authenticated OCI APIs confirm Hyderabad is the home region, A1 billing is `LIMITED_FREE`, and the official Ubuntu ARM64 image is compatible. Tenancy inventory found no existing compute, boot or block allocations. One availability domain is available. The 2-OCPU/12-GB launch returned `InternalError: Out of host capacity`; Oracle's capacity report also returned `OUT_OF_HOST_CAPACITY` for both 2/12 and 1/6 A1 configurations.

Free networking resources were created and retained for retry. **No VM, boot disk, public IP, paid resource or account upgrade was created.** There is no running OCI API; public TLS, host firewall, reboot recovery and cloud inference remain unverified. A ₹0/month running deployment is not claimed. Account type is owner-confirmed; no direct Console shape-label observation or immediate billing-invoice verification is claimed.

Work is in isolated branch `deploy/oracle-always-free`, based on main `a1ff4ef8`. The other working copies, deployed Lotline frontend and existing production data were not modified.

## Executed checks

| Check | Evidence / result |
|---|---|
| Original baseline | `mvn -B -ntp verify` failed at test compilation due to obsolete tenant-unscoped mocks and constructors. |
| Repaired production suite | **16 tests pass**, no failures/errors/skips, with the actual ONNX service, real pgvector PostgreSQL and all eight Flyway migrations. The original dev-fallback unit tests remain labeled development-only; production integration tests activate `oracle`. |
| Supported server stack | Java 17 retained; Spring Boot updated from 3.2.5 to **4.1.2**, Springdoc to **3.2.0**, Testcontainers to **1.21.4**. Framework-managed dependencies replace the obsolete Micrometer pin; Hibernate uses native VECTOR mapping. |
| Session/security | Role escalation blocked, separate tenant reads/writes, real Secure HttpOnly refresh cookie, token rotation/replay rejection, untrusted Origin rejection and unsafe-route blocking passed. Long refresh tokens now use SHA-256 prehashing before BCrypt. Existing old refresh cookies need a fresh sign-in. |
| Model artifact | Exact ONNX Runtime **1.17.3** JAR inspected: both Linux AArch64 native libraries exist. The committed model loads and returns 128 finite, normalized dimensions. No model/preprocessing replacement or hash fallback was used in production tests. |
| Production images | ARM64 application image built, with Java **17.0.21**, UID **10001**, bounded heap and C1 compilation for the small server. PostgreSQL **16.14 / pgvector 0.8.2** and Caddy **2.11.9** have pinned multi-architecture manifests. |
| Configuration | Production Compose validation, ARM64 Caddy configuration validation, shell syntax and Python compilation passed. |
| Cost guard | **9 tests pass**, also under `python -O`; guards reject pre-existing CPU/RAM/storage over-allocation, paid/wrong-region shapes, missing inventory/quota and insufficient quota. Saved-network retries reject changed tenancy/region/allocation, an existing VM and uncertain launch outcomes. They do not use disableable Python assertions. |
| Authenticated OCI preflight | Passed after supplying the availability domain required by the A1 quota API. A large service quota is not a free allowance: the independent 2-OCPU/12-GB/200-GB ceilings are still enforced. |
| OCI network rules | Read back from OCI: the public subnet uses only the custom security list; TCP 22 is restricted to this workstation's /32, 80/443 are public, and only fragmentation-needed ICMP is additionally allowed. The saved internet-gateway route matches. No database/application port is opened. Private report: `reports/oracle/cloud-network-verification.json`. |
| Saved-network retry | Executed against the authenticated tenancy. Repeated preflight passed, capacity remained `OUT_OF_HOST_CAPACITY`, no second VM launch was attempted, and every saved resource ID remained unchanged. Private evidence: `reports/oracle/cloud-retry.log`. The VM-success path remains unverified until host capacity returns. |
| Live local acceptance | **Passed** on Docker Desktop with ARM64 emulation: actual API/database `uname -m=aarch64`, trusted local HTTPS, real 128-dimensional ONNX output (norm 1.0000000138165843), two-tenant isolation, secure-cookie refresh, UID 10001/read-only root, private PostgreSQL, accepted record surviving API/database restart, isolated dump restoration, and a deliberately failed image update restoring the previous image and record. Private evidence: `reports/oracle/local-validation.json`. |
| Native ARM64 CI | **Passed** on native `aarch64`, Ubuntu 24.04, Temurin 17.0.21: [run 43941397967](https://github.com/dineshsuthar123/supplychain-auth/actions/runs/43941397967), code commit `ed9ba968`. All 16 backend tests, six cost guards (normal and optimized Python), image build and the complete HTTPS/image/persistence/restore/failed-release rollback exercise passed. Actual mapped libraries are `/opt/onnx/libonnxruntime.so` and `/opt/onnx/libonnxruntime4j_jni.so`; `/tmp` remains noexec. This is native ARM hardware proof, not an OCI VM deployment. |

The first native run exposed JNI extraction failing on the container's noexec `/tmp`; ARM emulation had not caught it. The corrected Dockerfile extracts the exact ONNX 1.17.3 ARM64 libraries into root-owned `/opt/onnx` at build time and uses ONNX's native-path property. No native-library version, model, preprocessing or embedding fallback was substituted.

## Infrastructure and cost inventory

| Item | Planned | Actually created / verified in OCI |
|---|---|---|
| Region / OS | Hyderabad `ap-hyderabad-1` home region / Ubuntu 24.04 ARM64 | Home region verified by API; compatible Ubuntu image selected; no guest OS running |
| Compute | One `VM.Standard.A1.Flex`, 2 OCPUs, 12 GB RAM | None; 0 OCPUs / 0 GB allocated by this work |
| Storage | 50 GB boot; all tenancy boot/block volumes at most 200 GB | None; 0 GB allocated by this work; existing boot/block usage was 0 GB |
| Networking | VCN, internet gateway, routes, restricted security list, subnet, VNIC, ephemeral public IPv4 | One VCN, one internet gateway, one custom route table, one custom security list, one public subnet; the VCN also generated its default route table, unused default security list and DHCP options. No VNIC/public IP. |
| Public API / TLS | User-controlled subdomain, automatic Caddy certificate; SSH tunnel first if absent | No public hostname or certificate configured |
| Account / billing | Free Tier, no PAYG upgrade, no trial-dependent resources | Owner confirmed Free Tier. No upgrade, paid resource or credit-dependent compute was created. Actual cost-report ingestion/invoice is not independently verified. |

The retained VCN/network configuration fits the free VCN allowance. With no VM, volume or public IP allocated, no compute/storage charge was introduced. There is no paid fallback, managed database, load balancer, NAT gateway, object-storage bucket or blockchain funding. No trial credits are relied upon. This does not certify an unobserved account invoice. Resource IDs, account metadata, keys and IP addresses stay in ignored private state. Operations, safe capacity retry, backup/recovery and the five-backend plan are in [README.md](README.md).

## Audit findings and boundaries

The source scan found 12 default-credential occurrences. Gitleaks **8.30.1**, downloaded from its official release and SHA-256 verified, found five historical generic-API-key/Kubernetes-secret matches on main history. A redacted scan of the deployment branch's new commits passed with no findings. The redacted findings are private under `reports/oracle/`. Historical defaults/potential credentials must never be reused; rotate any that were used live. This work does not rewrite repository history or claim to have revoked unknown external credentials. Production secrets are freshly generated and excluded from Git and Docker context. Tracked product-service build output was removed from the index; source and development configuration remain available.

The Oracle policy blocks SIWE entirely because its signature verification is incomplete, along with legacy bypass/raw-embedding routes. No wallet private key or blockchain funding is used. JWTs/credentials are tested over HTTPS with an explicitly trusted local test CA; this does not prove a public certificate. The production proxy will use ordinary public certificate validation after actual DNS/port setup.

The legacy benchmark controller is excluded from the Oracle profile, avoiding initialization of its synthetic in-memory product store. A production-profile test also checks that the controller bean is absent. Local development profiles retain their existing behavior.

The unchanged SupplyPrint SPA persists access tokens in localStorage, has no automatic refresh, and performs client-only logout. It is not declared browser-production-ready by this backend deployment. Frontend routing/session work is separate. The current deployed Lotline app has a different API and is not repointed here.

The controlled image is an MIT-licensed Fashion-MNIST test capture, losslessly encoded as PNG with its license/source/hash in test resources. Self-matching inference and API plumbing are **not anti-counterfeit accuracy evidence**. There is no field-accuracy, six-project throughput or cloud-capacity claim.

The packaged model is byte-for-byte identical to the committed 4,439,288-byte file: SHA-256 `653f26fee45d6e90e68819634dde74c3662550f7e1cdc17ae1d5e1ee39f17330`. The native-path setting is supported by the [pinned upstream loader](https://github.com/microsoft/onnxruntime/blob/v1.17.3/java/src/main/java/ai/onnxruntime/OnnxRuntime.java).

## Required cloud acceptance

When A1 capacity becomes available, use `retry-launch` with the saved network, fresh factual eligibility evidence and the original approved SSH source/key. It repeats inventory and capacity checks without creating duplicate networking. Then verify both SSH identities, apply/inspect the host firewall, deploy the CI-validated immutable image, test actual HTTPS auth/image/tenant behavior, restart and safely reboot the VM, restore its backup in isolation, and inspect resource usage/billing. Keep old data/volumes until an explicitly reviewed cutover; do not restore unrelated Lotline data into this SupplyPrint schema. No background capacity-polling or retry automation was installed.

The verified local ARM64 Docker stack is the available zero-hosting-cost demo while waiting. The Always Free E2 micro shape has only 1 GB RAM and does not meet this deployment's validated single-VM budget; it was not silently substituted. There is no claim that a different region or paid shape would satisfy the stated constraints.
