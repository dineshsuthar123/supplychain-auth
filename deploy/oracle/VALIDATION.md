# Oracle deployment validation

## Actual status

**OCI deployment: not performed.** No usable OCI credentials or connected console are available. Account type, home-region usage, capacity, actual bill, public DNS/certificate, OCI/Ubuntu firewall behavior and VM reboot recovery remain unverified. No OCI resource or paid subscription was created. A ₹0/month running deployment is not claimed.

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
| Cost guard | **6 tests pass**, also under `python -O`; guards reject pre-existing CPU/RAM/storage over-allocation, paid/wrong-region shapes, missing inventory/quota and insufficient quota. Reuse is counted without allocating a duplicate VM. They do not use disableable Python assertions. |
| Live local acceptance | **Passed** on Docker Desktop with ARM64 emulation: actual API/database `uname -m=aarch64`, trusted local HTTPS, real 128-dimensional ONNX output (norm 1.0000000138165843), two-tenant isolation, secure-cookie refresh, UID 10001/read-only root, private PostgreSQL, accepted record surviving API/database restart, isolated dump restoration, and a deliberately failed image update restoring the previous image and record. Private evidence: `reports/oracle/local-validation.json`. |
| Native ARM64 CI | The first native `ubuntu-24.04-arm` run passed all 16 backend tests, including real inference, but exposed JNI extraction failing on the container's noexec `/tmp`. The Dockerfile now extracts the exact 1.17.3 ARM64 libraries into root-owned `/opt/onnx` at build time and uses ONNX's native-path property. Temporary storage remains noexec; acceptance checks inspect actual mapped library paths. The corrected full-stack run must pass before deployment. |

## Audit findings and boundaries

The source scan found 12 default-credential occurrences. Gitleaks **8.30.1**, downloaded from its official release and SHA-256 verified, found five historical generic-API-key/Kubernetes-secret matches on main history. The redacted findings are private under `reports/oracle/`. Historical defaults/potential credentials must never be reused; rotate any that were used live. This work does not rewrite repository history or claim to have revoked unknown external credentials. Production secrets are freshly generated and excluded from Git and Docker context. Tracked product-service build output was removed from the index; source and development configuration remain available.

The Oracle policy blocks SIWE entirely because its signature verification is incomplete, along with legacy bypass/raw-embedding routes. No wallet private key or blockchain funding is used. JWTs/credentials are tested over HTTPS with an explicitly trusted local test CA; this does not prove a public certificate. The production proxy will use ordinary public certificate validation after actual DNS/port setup.

The legacy benchmark controller is excluded from the Oracle profile, avoiding initialization of its synthetic in-memory product store. A production-profile test also checks that the controller bean is absent. Local development profiles retain their existing behavior.

The unchanged SupplyPrint SPA persists access tokens in localStorage, has no automatic refresh, and performs client-only logout. It is not declared browser-production-ready by this backend deployment. Frontend routing/session work is separate. The current deployed Lotline app has a different API and is not repointed here.

The controlled image is an MIT-licensed Fashion-MNIST test capture, losslessly encoded as PNG with its license/source/hash in test resources. Self-matching inference and API plumbing are **not anti-counterfeit accuracy evidence**. There is no field-accuracy, six-project throughput or cloud-capacity claim.

The packaged model is byte-for-byte identical to the committed 4,439,288-byte file: SHA-256 `653f26fee45d6e90e68819634dde74c3662550f7e1cdc17ae1d5e1ee39f17330`. The native-path setting is supported by the [pinned upstream loader](https://github.com/microsoft/onnxruntime/blob/v1.17.3/java/src/main/java/ai/onnxruntime/OnnxRuntime.java).

## Required cloud acceptance

After access is configured: inventory/Console free eligibility, provision or inspect the approved VM, verify both SSH identities, apply and inspect OCI and host firewalls, deploy the tested immutable image, validate actual HTTPS auth/image/tenant behavior, restart and safely reboot that VM, restore its backup in isolation, then check resource usage and billing. Keep the old data/volumes until an explicitly reviewed cutover; do not restore unrelated Lotline data into this SupplyPrint schema.
