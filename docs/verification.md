# Executed verification — 12 October 2026

This report covers local execution and the public Vercel/Render demo against real PostgreSQL and real APIs. Hosting access was authorized and a free temporary database was explicitly selected. No paid plan changes were made. The demo database expires on **11 November 2026**; durable operations need a separate hosting and recovery decision.

## Public deployment verification

On 12 October 2026, [the frontend](https://supplyledger.vercel.app/) returned HTTP 200 and [Render readiness](https://supplychain-auth.onrender.com/actuator/health/readiness) returned UP. [GitHub CI](https://github.com/dineshsuthar123/supplychain-auth/actions/runs/38284301905) passed both backend and frontend/E2E jobs for `de5f6c8f`.

- The complete synthetic smoke ran through the actual HTTPS frontend proxy: 1,180 recovered, 20 accountable loss, incident CLOSED, projections matched. Its ZIP verified all seven payloads at sequence 46.
- After the hosted runtime restart reached LIVE, the same workspace, incident status and exact quantities were read again and matched.
- Live database sessions use the restricted runtime role; accepted-event UPDATE and schema CREATE are denied. Flyway uses separate owner credentials.
- A 144,063-byte logical backup of the hosted lotline schema was restored into a separate local PostgreSQL database. A separate API verified the same closed incident, journal projection and all seven re-exported payload hashes. The cloud database was not restored or replaced. Paid provider PITR remains untested.
- Windows start/stop helpers built the application in an isolated Compose project and served the frontend successfully. Stop preserved the PostgreSQL volume.
- Vitest 4.1.11, typecheck, lint, formatting, unit test and build passed. Fresh local and Vercel installs reported zero npm audit findings.
- All **14 workflow/interface/HTTPS-cookie checks** passed on the full public browser run, including the coffee case, owner creation, cookie rotation/logout, stale recall confirmation and four responsive widths. No check was skipped. The performance harness initially failed to expose its measurement-library global in the initialization scope; that setup was corrected and the isolated measurement rerun passed. Product code and CSP were unchanged.
- [Public web-vitals sample](web-vitals-public.json): **LCP 2,412 ms, INP 176 ms, CLS 0**, at the public URL with a fresh Chromium context, CPU slowed 4×, added 150 ms latency and 1.6 Mbps download. One lab sample, not a field percentile or throughput guarantee.

The cinematic frontend follow-up re-ran typecheck, lint, formatting, the decimal unit test, the production build and the complete real-API browser suite. Its original cast, intro, motion controls and design references are documented in [frontend-direction.md](frontend-direction.md). Backend and container results below are from the preceding pivot validation; backend code was unchanged in this frontend follow-up.

## Commands and results

| Check | Executed command / result |
|---|---|
| Backend | `backend/mvnw.cmd -B -ntp -f backend/pom.xml verify` — **24 tests passed**, zero failures/errors/skips; executable jar built. |
| Fresh / legacy database | PostgreSQL 17.9 Testcontainers applied all three migrations to an empty database, and separately preserved legacy `public` account/history sentinel rows. No H2 replacement. |
| Frontend | `npm ci`, `npm run typecheck`, `npm run lint`, `npm run format:check`, `npm test`, `npm run build` — passed. One unit test exercises decimal arithmetic beyond JavaScript safe-integer precision. |
| Browser | `npx playwright test` — **14 passed, 1 skipped** in 2.3 minutes. Real production-preview/API suite includes cinematic interactions, receipt/incident checks, and the complete coffee case. Hosted HTTPS proxy test explicitly requires deployment and is skipped locally. |
| Container | `docker build -t lotline:local backend` — passed. Java 21 non-root user `lotline`, `PORT=10000`, database supplied as PostgreSQL URI, memory limit 512 MiB, readiness UP. Observed warm usage 251.5 MiB; this is one local observation, not a capacity guarantee. |
| Smoke | `python scripts/smoke.py --base http://localhost:8081 --output artifacts/docker-smoke-final` — completed separate synthetic coffee workspace through close/export/projection comparison. |
| Packet | `python scripts/verify-evidence.py artifacts/docker-smoke-final/lotline-evidence.zip` — verified all **7 payload files**, sequence 46. Integration tests reject changed bytes and validate snapshot scope. |
| Restart | Restarted `lotline-docker-smoke`, then `python scripts/check-persistence.py artifacts/docker-smoke-final/fixture.json --base http://localhost:8081` — same workspace, CLOSED incident, 1,180 recovered, 20 accountable loss, zero nonterminal affected quantity, projection matches. Browser reload/session rotation also checked. |
| API contract | `python scripts/generate-api.py --check` — generated OpenAPI command schemas and TypeScript DTOs match source. |
| Deployment | Render schema and Compose checks passed during local validation. After the actual HTTPS upstream was configured, strict `python scripts/check-deployment.py` passed. |
| Runtime privileges | Ran the documented privilege script against the local synthetic database: runtime role can SELECT accepted events; UPDATE is denied. Separate application/Flyway credentials require explicit hosted configuration. |

The domain tests cover partial conservation and declared loss, exact precision, merge/diamond contribution deduplication, whole commingled output exposure, receiver-only receipts, concurrent duplicate/competing commands, recall/dispatch races, reservations and in-transit holds, stale previews, independent acknowledgment versus recovery, overlapping incidents, late evidence/reopening, justified closure, historical snapshots, site/workspace/partner isolation, revoked permission rechecks, export escaping/scope, projection rebuild, and outbox crash/lease/retry/idempotent delivery.

Issues found during execution were corrected: PostgreSQL rejects a negative proposed insert before UPSERT conflict handling; journal debits now update an existing balance. The historical command lookup uses `jsonb_exists` rather than a JSON operator misread as a JDBC parameter. Mobile scroll regions and the read-only evidence dock now support keyboard focus. Browser selectors distinguish a native disposition select and a recovery target from an unresolved target for the same lot.

## Visual and interaction review

The public story, login, trace, handoff list, workspace and evidence screens are captured at **360, 768, 1024 and 1440 px**. The expanded coffee browser test captures the actual 500-sent/480-counted receiver form and the activated incident matrix at those same widths. These use accepted server data. Public sample data is explicitly synthetic.

- [Desktop trace](screenshots/trace-1440.png), [mobile trace](screenshots/trace-360.png), [public story](screenshots/story-1440.png).
- [Mobile receiver assertion](screenshots/receipt-360.png), [desktop receiver assertion](screenshots/receipt-1440.png).
- [Mobile incident](screenshots/incident-360.png), [desktop incident matrix](screenshots/incident-1440.png).

Checks include no document-level horizontal overflow, long lot/organization labels, serious/critical axe violations, reduced motion, keyboard edge selection/deep-link reload, dialog focus/Escape, owner onboarding, session reload, stale recall confirmation and explicit offline/backend-unavailable recovery. Horizontally scrollable tables preserve complete values and are keyboard focusable. No claim of formal accessibility certification is made.

The cinematic suite also verifies automatic intro completion, first-visit versus returning-visit behavior, Skip/Escape/replay, three camera chapters, orbit, offscreen pause, synthetic case reset, persisted manual motion preference, password-field character focus, real route transitions, stable query-only graph selection and command palette access. Four additional screenshots show the opening and scene at 360 / 1440 px; the opening screenshot samples the actual composed animation at 1.85 seconds. A decorative SVG treatment corrected the first mobile contrast finding.

## Newly measured performance

[Raw benchmark](benchmark.json) / [query plans](query-plans.txt) / [reproduction queries](../scripts/explain-queries.sql).

Hardware: Windows 11, Ryzen 7 8845HS (8 cores/16 threads), 15.31 GiB system RAM, Docker Desktop Linux allocation 7.59 GiB. Native Java 21.0.9 API, PostgreSQL 17.9, pool size 5, warm loopback connection. The isolated benchmark finishes with 200 lots, 200 holdings, sequence 243 and **zero lineage edges**; it is a sparse fixture, not a large genealogy benchmark. Four commissioning clients contend on one workspace guard.

| Workload | Samples / concurrency | Throughput | p50 / p95 / p99 | Failures |
|---|---|---|---|---|
| Trace read at 120 lots | 200 / 4 | 29.02 requests/s | 107.69 / 146.25 / 158.07 ms | 0 |
| Commission stock | 80 / 4 | 35.21 commands/s | 91.86 / 140.71 / 144.10 ms | 0 |
| Preview → activate → acknowledge → recover → close | 10 / 1 | 3.51 five-request lifecycles/s | 258.23 / 289.44 / 289.44 ms | 0 |

The planner chose sequential scans for tiny journal/event tables and indexed lookups for the coffee fixture's two descendant edges and due outbox predicate. It was not forced to use indexes. The outbox was drained, so its measured selection returned zero rows; lease/concurrency behavior is covered separately by integration tests. These plans and loopback figures do not predict cloud latency or larger tenant performance. Run `scripts/load-test.py` only against an isolated synthetic environment.

[Public-entry web-vitals sample](web-vitals.json): **LCP 2,448 ms, INP 80 ms, CLS 0** for the cinematic frontend. Fresh Chromium context at 1440×1000, CPU slowed 4×, network latency 150 ms, 1.6 Mbps download / 0.75 Mbps upload, gzip-compressed production preview. The preceding pivot's final sample was 2,108 / 128 / 0; the cinematic follow-up adds original SVG artwork, the opening, and lazily loaded GSAP / ScrollTrigger. Daily app feature routes and graph remain separate chunks. This is a lab sample with two interactions, not a field percentile or deployed-provider measurement.

## Deployment scope and product limits

Verified local URLs: frontend `http://localhost:4173`, native readiness `http://localhost:8080/actuator/health/readiness`, container readiness `http://localhost:8081/actuator/health/readiness`.

See [deployment.md](deployment.md) for actual project settings, observed provider build versions and the temporary database expiry. The configured upstream is `https://supplychain-auth.onrender.com`. Local benchmark results remain local measurements; public smoke success does not establish capacity for larger business workloads.

Partner reports can be wrong or absent. Transfer responsibility is not confirmed physical location. Unknown custody observations are evidence gaps, not extra inventory. Holds enforce connected Lotline commands; they cannot stop external movement. Manifest hashes verify bytes, not physical authenticity or an independent trust anchor. Same-product/base-unit transformations only; no kg-to-piece conversion. Temporary free hosting can sleep and its database can expire; it is unsuitable for durable operational records without an owner-selected durable plan.
