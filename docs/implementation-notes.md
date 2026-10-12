# Lotline implementation notes

Starting commit: `8b0fb2747e8518d83a6b0812288f2137524134dc`.
Starting branch: `codex/supplyprint-production-validation`; implementation branch: `feat/lotline-workbench`.
No AGENTS.md was found in the repository or its parent directories. The initial tracked changes were generated Java classes/jars and compiler lists. Untracked datasets, reports, local secrets, experiments, and the original working tree were preserved outside the repository at `../lotline-user-backup-20261012`; they are not part of Lotline or a deployment.

## Audit and consolidation

| Component | Decision | Finding |
|---|---|---|
| BCrypt password hashing | Keep approach | Passwords were already hashed; new per-device sessions replace one refresh hash on each user. |
| Request tracing, exception advice, Micrometer | Keep approach | Bound incoming trace IDs and clear MDC in finally. Stable domain errors replace generic exceptions. |
| Transactional persistence / Testcontainers | Keep approach | Plain PostgreSQL is the authority; no H2 or synthetic verification tests. |
| CRA App.jsx / auth | Replace | Component-state routes, localStorage tokens, raw refresh JSON, image capture and dashboard metrics. Vite/TS, real routes, in-memory access tokens and cookie rotation. |
| Product-service / legacy controllers | Replace | Java 17 / Boot 3.2.5; fingerprint, product, wallet and bypass controllers share an unrelated model. One Java 21 feature-organized application. |
| Configuration binding | Replace | Competing property names, enabled RPC, vector/model requirements, legacy profiles, unsafe signing default. Explicit required secret and isolated database schema. |
| TenantContext implementations | Replace | Both common and product-service define tenant contexts. One authenticated session/membership principal; jobs carry explicit workspace IDs. |
| Flyway V1–V8 in public | Remove from active scan | Preserve deployed public schema and historical checksums. New `lotline.lotline_schema_history`, no destructive legacy migration or inferred genealogy. |
| Blockchain outbox | Replace | RPC submission and retries become leased, database-local inbox delivery; holds commit synchronously. |
| event-service, verification-service, common | Delete active code | Duplicate authentication/tenant/security, Mongo/Redis/Stripe/Web3 dependencies. |
| model, ML/image validation, contracts, godmode/realworld | Delete active code | Outside product contract; history remains the archive. |
| root/CRA Vercel, Fly/Railway/K8s, generated artifacts | Replace/delete | One frontend Vercel config; one Render Docker API and PostgreSQL. |

## Shared vocabulary

Genealogy records consumed inputs contributing to newly commissioned output lots. Custody records the same lot moving between sites. Holdings are current exact-decimal quantities; consumed parents are historical facts. A short receipt creates destination stock and unresolved transfer stock. Acknowledgment is a response, recovery is a quantity action. Evidence is a partner assertion recorded at a committed sequence, not a physical authenticity claim.

Implementation uses JDBC and explicit API DTOs, rather than JPA entities. Every accepted domain mutation locks the workspace row, validates membership, checks persistent idempotency, appends an event/journal, updates projections and writes notifications in one transaction. Query snapshots use a consistent transaction. Same-base-unit, same-product transformations only; maximum three decimal places with no rounding.

## Version sources

Java 21 and Spring Boot 4.0.8: [official system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html) and its published Maven parent. Boot 4.0 is the supported baseline selected instead of the retired 3.2 line; managed dependencies follow its BOM. React: [versions](https://react.dev/versions); Vite: [requirements](https://vite.dev/guide/); [Router installation](https://reactrouter.com/start/declarative/installation); [Query installation](https://tanstack.com/query/latest/docs/framework/react/installation); [GSAP installation](https://gsap.com/docs/v3/Installation/). Exact package releases are checked against npm registry and committed in the lockfile.

Deployment selectors and paths: [Render Blueprint reference](https://render.com/docs/blueprint-spec), [free service limits](https://render.com/docs/free), [Vercel external rewrites](https://vercel.com/docs/rewrites), [cache headers](https://vercel.com/docs/caching/cache-control-headers). Live deployment requires account access and a concrete Render upstream.
