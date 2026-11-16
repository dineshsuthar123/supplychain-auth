# Oracle deployment readiness — 16 November 2026

Target: the **SupplyPrint service on `main` at a1ff4ef8**, in an isolated `deploy/oracle-always-free` worktree. The separate Lotline checkout is preserved. Oracle deployment is not yet verified.

## Initial inspection and baseline

- Runtime: one Java 17 / Spring Boot 3.2.5 product-service, PostgreSQL with pgvector, ONNX Runtime Java 1.17.3 and the committed `models/fingerprint.onnx`. No Kafka, MongoDB, Redis, separate event/verification service or blockchain node is required. Blockchain will be disabled.
- The exact ONNX 1.17.3 Maven JAR contains `linux-aarch64/libonnxruntime.so` and `libonnxruntime4j_jni.so`. Actual ARM execution and model inference are required separately; archive contents are not an inference result.
- Baseline `mvn -B -ntp verify` **fails at test compilation**: tests reference old unscoped repository methods and an obsolete ProductFingerprint constructor. Existing integration tests activate the development embedding and send unauthenticated privileged requests; they do not prove production image inference or authorization.
- Deployment blockers: default signing/database credentials, public detailed health/documentation, incomplete SIWE implementation, missing auth-route rate limiting, missing signup refresh cookie, no independent migration/runtime database identities, and exposed development database/monitoring ports. There is no CI workflow on this default branch.
- A redacted scan of tracked text found 12 default-credential occurrences, including historical/generated files. No private-key/provider-token literal was detected by that scan. This is not a comprehensive historical secret-audit guarantee. Production files must not inherit these defaults or copy tracked build output.
- No OCI CLI credentials (`~/.oci`), SSH configuration/keys, OCI environment variables or usable connected cloud console were found. The Windows helper returned `native pipe ... cannot find the file specified` twice. Account type, home region, existing allocations, capacity and billing therefore remain unverified. **No OCI resources have been created.**

## Planned implementation

1. Repair stale tests and add production image/auth/tenant tests without enabling the development fallback.
2. Isolate the Oracle profile, enforce secrets/disabled SIWE/disabled blockchain, and restrict public ingress.
3. Build ARM64 application/database/proxy images; execute actual model, pgvector and migration checks.
4. Add production Compose, a shared HTTPS proxy, constrained host operations, backup/isolated restore, monitoring and a guarded OCI provisioning path.
5. Validate locally before requesting missing cloud access. Provision only after independently checking the tenancy's home region, Free Tier status and all existing allocations.

## Cost constraints and primary sources

The deployment ceiling is **one VM.Standard.A1.Flex, 2 OCPUs, 12 GB RAM, 50 GB boot volume initially, no extra block volumes**. All tenancy boot/block storage must total at most 200 GB. No NAT gateway, paid load balancer, Kubernetes, account upgrade, domain purchase or trial-funded resource is permitted. Hyderabad is usable only if it is the tenancy's home region and Always Free capacity is available.

[Oracle Always Free resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) currently documents 1,500 OCPU-hours / 9,000 GB-hours monthly (2 OCPUs / 12 GB), home-region eligibility, 200 GB combined volumes and idle reclamation. A Free Tier tenancy has 20 GB combined Object Storage and 50,000 requests/month; actual remaining capacity must be verified before off-VM backups. Local backups alone are not disaster recovery.

[Oracle ARM support](https://docs.oracle.com/en-us/iaas/Content/Compute/References/arm.htm) lists Ubuntu as supported. There is no claim yet of a running VM, public certificate, actual OCI cost compliance, VM reboot recovery or off-VM restore.

## Repository preparation completed — 17 November 2026

The baseline blockers are addressed by the isolated Oracle profile and deployment package. The final backend suite has 16 passing tests; ARM64-emulated and native ARM64 Compose stacks passed HTTPS, actual image inference, tenant isolation, restart persistence, isolated backup restore and failed-release rollback. Native testing caught and resolved ONNX JNI loading from noexec temporary storage while preserving the pinned model/runtime. Production excludes the legacy benchmark initializer. Successful native CI and pending cloud evidence are tracked in [VALIDATION.md](VALIDATION.md).

Spring Boot 3.2.5 was obsolete. The 3.5 line [reached its final open-source release](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/), so the service now uses 4.1.2 with Java 17 and temporary Jackson 2 compatibility to preserve its existing API. ONNX Runtime 1.17.3, the committed model and its preprocessing semantics remain unchanged. No frontend or cloud cutover was performed.

## OCI access and launch attempt — 17 November 2026

The owner supplied a local OCI profile and confirmed Free Tier/Hyderabad. Authentication, home region, empty compute/storage inventory, free-eligible A1 shape and compatible Ubuntu image are verified. The live quota request required an availability-domain argument; the provisioning code is corrected. Free networking was created and its actual ingress/routing read back successfully. A1 launch failed with Oracle's **Out of host capacity**; capacity reports show both 2/12 and 1/6 unavailable in the single availability domain. No VM, volume or paid resource was created, and no account upgrade occurred. Saved-network retries and nine cost/state guards are implemented; [VALIDATION.md](VALIDATION.md) separates this actual cloud state from the successful native ARM64 application tests.
