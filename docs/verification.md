# Executed verification — 12 October 2026

This report covers local execution against real PostgreSQL and real application APIs. Live Vercel/Render URLs, provider TLS/cookie forwarding, hosted CI and paid backup restoration remain unverified. No hosting credentials or billing commitments were used.

## Commands and results

| Check | Executed command / result |
|---|---|
| Backend | `backend/mvnw.cmd -B -ntp -f backend/pom.xml verify` — **24 tests passed**, zero failures/errors/skips; executable jar built. |
| Fresh / legacy database | PostgreSQL 17.9 Testcontainers applied all three migrations to an empty database, and separately preserved legacy `public` account/history sentinel rows. No H2 replacement. |
| Frontend | `npm ci`, `npm run typecheck`, `npm run lint`, `npm run format:check`, `npm test`, `npm run build` — passed. One unit test exercises decimal arithmetic beyond JavaScript safe-integer precision. |
| Browser | `npx playwright test` — **9 passed, 1 skipped** in 2.2 minutes. Real production-preview/API suite includes expanded receipt/incident checks. Hosted HTTPS proxy test explicitly requires deployment and is skipped locally. |
| Container | `docker build -t lotline:local backend` — passed. Java 21 non-root user `lotline`, `PORT=10000`, database supplied as PostgreSQL URI, memory limit 512 MiB, readiness UP. Observed warm usage 251.5 MiB; this is one local observation, not a capacity guarantee. |
| Smoke | `python scripts/smoke.py --base http://localhost:8081 --output artifacts/docker-smoke-final` — completed separate synthetic coffee workspace through close/export/projection comparison. |
| Packet | `python scripts/verify-evidence.py artifacts/docker-smoke-final/lotline-evidence.zip` — verified all **7 payload files**, sequence 46. Integration tests reject changed bytes and validate snapshot scope. |
| Restart | Restarted `lotline-docker-smoke`, then `python scripts/check-persistence.py artifacts/docker-smoke-final/fixture.json --base http://localhost:8081` — same workspace, CLOSED incident, 1,180 recovered, 20 accountable loss, zero nonterminal affected quantity, projection matches. Browser reload/session rotation also checked. |
| API contract | `python scripts/generate-api.py --check` — generated OpenAPI command schemas and TypeScript DTOs match source. |
| Deployment | `python scripts/check-deployment.py --allow-unconfigured --official-schema` and `docker compose --env-file artifacts/local-runtime.env config --quiet` — passed. Strict deployment check deliberately blocks until the real HTTPS upstream is configured. |
| Runtime privileges | Ran the documented privilege script against the local synthetic database: runtime role can SELECT accepted events; UPDATE is denied. Separate application/Flyway credentials require explicit hosted configuration. |

The domain tests cover partial conservation and declared loss, exact precision, merge/diamond contribution deduplication, whole commingled output exposure, receiver-only receipts, concurrent duplicate/competing commands, recall/dispatch races, reservations and in-transit holds, stale previews, independent acknowledgment versus recovery, overlapping incidents, late evidence/reopening, justified closure, historical snapshots, site/workspace/partner isolation, revoked permission rechecks, export escaping/scope, projection rebuild, and outbox crash/lease/retry/idempotent delivery.

Issues found during execution were corrected: PostgreSQL rejects a negative proposed insert before UPSERT conflict handling; journal debits now update an existing balance. The historical command lookup uses `jsonb_exists` rather than a JSON operator misread as a JDBC parameter. Mobile scroll regions and the read-only evidence dock now support keyboard focus. Browser selectors distinguish a native disposition select and a recovery target from an unresolved target for the same lot.

## Visual and interaction review

The public story, login, trace, handoff list, workspace and evidence screens are captured at **360, 768, 1024 and 1440 px**. The expanded coffee browser test captures the actual 500-sent/480-counted receiver form and the activated incident matrix at those same widths. These use accepted server data. Public sample data is explicitly synthetic.

- [Desktop trace](screenshots/trace-1440.png), [mobile trace](screenshots/trace-360.png), [public story](screenshots/story-1440.png).
- [Mobile receiver assertion](screenshots/receipt-360.png), [desktop receiver assertion](screenshots/receipt-1440.png).
- [Mobile incident](screenshots/incident-360.png), [desktop incident matrix](screenshots/incident-1440.png).

Checks include no document-level horizontal overflow, long lot/organization labels, serious/critical axe violations, reduced motion, keyboard edge selection/deep-link reload, dialog focus/Escape, owner onboarding, session reload, stale recall confirmation and explicit offline/backend-unavailable recovery. Horizontally scrollable tables preserve complete values and are keyboard focusable. No claim of formal accessibility certification is made.

## Newly measured performance

[Raw benchmark](benchmark.json) / [query plans](query-plans.txt) / [reproduction queries](../scripts/explain-queries.sql).

Hardware: Windows 11, Ryzen 7 8845HS (8 cores/16 threads), 15.31 GiB system RAM, Docker Desktop Linux allocation 7.59 GiB. Native Java 21.0.9 API, PostgreSQL 17.9, pool size 5, warm loopback connection. The isolated benchmark finishes with 200 lots, 200 holdings, sequence 243 and **zero lineage edges**; it is a sparse fixture, not a large genealogy benchmark. Four commissioning clients contend on one workspace guard.

| Workload | Samples / concurrency | Throughput | p50 / p95 / p99 | Failures |
|---|---|---|---|---|
| Trace read at 120 lots | 200 / 4 | 29.02 requests/s | 107.69 / 146.25 / 158.07 ms | 0 |
| Commission stock | 80 / 4 | 35.21 commands/s | 91.86 / 140.71 / 144.10 ms | 0 |
| Preview → activate → acknowledge → recover → close | 10 / 1 | 3.51 five-request lifecycles/s | 258.23 / 289.44 / 289.44 ms | 0 |

The planner chose sequential scans for tiny journal/event tables and indexed lookups for the coffee fixture's two descendant edges and due outbox predicate. It was not forced to use indexes. The outbox was drained, so its measured selection returned zero rows; lease/concurrency behavior is covered separately by integration tests. These plans and loopback figures do not predict cloud latency or larger tenant performance. Run `scripts/load-test.py` only against an isolated synthetic environment.

[Public-entry web-vitals sample](web-vitals.json): **LCP 2,108 ms, INP 128 ms, CLS 0**. Fresh Chromium context at 1440×1000, CPU slowed 4×, network latency 150 ms, 1.6 Mbps download / 0.75 Mbps upload, gzip-compressed production preview. The first lazy-story sample missed LCP at 3.32 s; rendering the complete SVG immediately and loading only GSAP separately removed the additional route waterfall. Daily app feature routes and graph remain separate chunks. This is a lab sample with two interactions, not a field percentile or deployed-provider measurement.

## Remaining deployment and product limits

Verified local URLs: frontend `http://localhost:4173`, native readiness `http://localhost:8080/actuator/health/readiness`, container readiness `http://localhost:8081/actuator/health/readiness`.

Follow [deployment.md](deployment.md): deploy Render API/database, set the exact frontend origin, run `node scripts/configure-vercel.mjs https://ACTUAL-API.onrender.com`, commit the concrete rewrite, import `frontend` into Vercel, then execute the HTTPS proxy smoke test. The current invalid upstream is an intentional deployment guard. Provider build versions, TLS, cookie forwarding and restore exercises require the actual accounts.

Partner reports can be wrong or absent. Transfer responsibility is not confirmed physical location. Unknown custody observations are evidence gaps, not extra inventory. Holds enforce connected Lotline commands; they cannot stop external movement. Manifest hashes verify bytes, not physical authenticity or an independent trust anchor. Same-product/base-unit transformations only; no kg-to-piece conversion. Temporary free hosting can sleep and its database can expire; it is unsuitable for durable operational records without an owner-selected durable plan.
