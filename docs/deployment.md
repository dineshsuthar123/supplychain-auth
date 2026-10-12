# Vercel frontend + Render API / PostgreSQL

The existing projects were updated on **12 October 2026**: [public frontend](https://supplyledger.vercel.app/) and [API readiness](https://supplychain-auth.onrender.com/actuator/health/readiness). The API now builds `backend/` from `feat/lotline-readiness`; Vercel builds `frontend/` with Vite. The concrete API rewrite is committed. Both returned HTTP 200 after deployment; readiness reported UP.

The owner explicitly selected a **free temporary demo database**. The new `lotline-demo-db` is in Oregon beside the existing API and expires **11 November 2026**. No paid plan changes were made. The root Blueprint remains a fresh-project Singapore template; it was not used to move the existing Oregon service. Original service settings were backed up privately before cutover.

Vercel's observed build used Node **22.23.2**, npm **10.9.8**, and builder CLI **62.7.0**; the local deploy CLI was **63.1.2**. Its build completed with zero npm audit findings. Local/CI pins remain Node 22.17.0 / npm 10.9.2. Vitest was updated to 4.1.11 and locally rechecked.

Public smoke, HTTPS cookie forwarding/rotation/logout, restart persistence and an isolated restore from the hosted logical backup passed; see [executed verification](verification.md). Runtime database access uses `lotline_runtime`; Flyway uses separate migration credentials. Render automatic deployments wait for branch checks to pass. Private provider credentials, project links, database dumps and synthetic-account passwords remain outside Git.

## 1. Choose an explicitly temporary or durable setup

The root Blueprint selects `free` for the API/database and `singapore` for both. It is a **temporary synthetic demo**. Render free web services can sleep after 15 minutes, and free PostgreSQL expires after 30 days without managed backups. An asleep scheduler cannot deliver timely notifications; holds still committed synchronously before sleep. The UI reports backend warming/offline and lets the user retry; there is no artificial keep-alive polling. [Official free limits](https://render.com/docs/free).

For durable operations, the owner must authorize paid selections before Blueprint sync: an always-on API `0.5c-512mb` and database `0.1c-256mb` are currently available lower-resource options; `1c-2g` API / `0.5c-1g` PostgreSQL provide more headroom. Validate capacity with the actual workload. Current plan IDs were checked in [Render compute plans](https://render.com/docs/compute-plans); legacy `starter` / `basic-*` names are not used. No upgrades were purchased.

## 2. Deploy Render first

1. Push the reviewed feature branch to the owner’s repository, then create a Render Blueprint from root `render.yaml`. Confirm service/database choices before creation.
2. API `rootDir: backend`, `dockerfilePath: ./Dockerfile`, `dockerContext: .` are relative to that root, as documented in [monorepo support](https://render.com/docs/monorepo-support). It is one Docker web service; no separate worker service is needed.
3. Set `TRUSTED_ORIGINS` to the exact chosen frontend HTTPS origin. A real custom hostname known before import is easiest; otherwise add the assigned Vercel production origin before testing login. Add preview origins individually only for isolated preview backends. No wildcard credentialed CORS.
4. Render supplies `DATABASE_URL` via the Postgres `connectionString` relationship. The tested environment converter parses percent-encoded credentials and creates a JDBC URL; raw `postgres://` is never passed as a JDBC URL. `DB_SSLMODE=require` enables encrypted Render connections. The actual hosted TLS connection remains part of deployment smoke verification.
5. Wait for migrations and `https://ACTUAL-API.onrender.com/actuator/health/readiness` to return `{"status":"UP"}`. Liveness is `/actuator/health/liveness`. `PORT` is supplied by Render; the server binds `0.0.0.0` and defaults to 8080 locally.

Docker pins Java 21.0.9 / Maven 3.9.16 (wrapper distribution checksum and image digests), uses a non-root JRE and bounded container heap. Pool size 5 is intentionally small against the documented 100-connection smaller PostgreSQL limit; account for migration/admin connections too. Graceful shutdown stops new work and leases recover after restart. State is PostgreSQL, never Render service disk.

| Variable | Meaning |
|---|---|
| `DATABASE_URL` | Render internal PostgreSQL URI; never exposed to frontend or logs |
| `JWT_SECRET` | Generated 256-bit-or-stronger signing / invitation derivation secret |
| `TRUSTED_ORIGINS` | Comma-separated exact browser origins |
| `SECURE_COOKIES=true` | Production `__Host-` cookie rules |
| `PORT` | Render HTTP binding |
| `DB_POOL_SIZE`, `DB_SSLMODE` | Connection bound / TLS mode |
| `MIGRATION_DB_USER`, `MIGRATION_DB_PASSWORD` | Optional separate schema owner; runtime privileges are hardened as described in security.md |
| `DB_HOST/PORT/NAME/USER/PASSWORD` | Local alternative when `DATABASE_URL` is absent |
| `OUTBOX_DELAY_MS` | Bounded internal delivery scheduler interval; default 2 seconds |

## 3. Configure and import Vercel

```sh
node scripts/configure-vercel.mjs https://ACTUAL-API.onrender.com
python scripts/check-deployment.py
```

Commit the concrete upstream change in **frontend/vercel.json**. Arbitrary environment placeholders are not interpolated there. Import the repository with project root `frontend`, Vite framework, `npm ci`, `npm run build`, output `dist`, Node **22.x**. Local/CI Node is pinned at 22.17.0. Vercel controls minor/patch rollout and exposes only a major selector; record its actual build version rather than promising an unavailable patch pin. [Official Node selection rules](https://vercel.com/docs/functions/runtimes/node-js/node-js-versions).

The first rewrite maps `/api/:path*` to the actual API with `/api/v1` and cookie paths preserved. Only known browser app/auth deep links fall back to index.html; APIs, assets and downloads are not swallowed. API caching is disabled at browser/CDN/rewritten origin; only fingerprinted public assets receive immutable caching. CSP restricts resources to self. Do not define database or signing secrets in `VITE_*`. Remove machine-local Vercel project links; none are committed.

Use a distinct Render database/backend for public previews; do not point them at operational production workspaces. Add only its precise preview frontend origins to that backend.

## 4. Smoke the deployed proxy

Create only isolated synthetic accounts and explicitly set a demo password:

```sh
python scripts/smoke.py --base https://ACTUAL-FRONTEND --origin https://ACTUAL-FRONTEND --output artifacts/deployed-smoke
python scripts/verify-evidence.py artifacts/deployed-smoke/lotline-evidence.zip
```

Set `APP_URL`, `SMOKE_EMAIL` and `SMOKE_PASSWORD` to that HTTPS frontend / isolated smoke owner, then run `npx playwright test tests/proxy.spec.ts` from frontend. It verifies host-only Secure/HttpOnly cookie forwarding, rotation on reload, logout deletion and no-store headers through Vercel → Render. This test is explicitly skipped locally; localhost cookies do not prove deployed proxy correctness. The broader domain E2E chooses the HTTPS cookie name automatically.

## Backups, cutover and rollback

Paid Postgres has continuous PITR: currently 3 days on Hobby and 7 days on Pro/higher workspace plans. Confirm the available recovery window in the database Recovery page before real use. Logical exports remain available separately. [Official recovery documentation](https://render.com/docs/postgresql-backups).

Restore exercise: select a known accepted sequence, create a recovery database at a prior available time, validate events/holdings/projection comparison in isolation, export and verify a packet, then repoint a staging API to it. Repoint production only after the owner reviews those results; retain the original database until validation completes. A recovery database can incur billing, so this exercise was documented, not purchased/executed here.

For a temporary free database, download private evidence packets and make `pg_dump -Fc --schema=lotline` backups before expiry. Store the credentials via `PGPASSFILE` / environment, not command history. Recreate a fresh database and restore that dump into the empty lotline schema/database; evidence ZIPs alone are not a complete operational database backup. Re-run projection and manifest checks before use.

Existing `public` legacy data/history remains untouched. Flyway creates only `lotline` with its separate history. Old migration files are removed from the active scan; do not baseline against or edit legacy checksums. No image/vector import into custody or genealogy is provided. Optional account/catalog import would require a separately reviewed explicit mapping.

Rollback to a prior **Lotline** artifact is possible only if it understands the current additive schema/events. Do not automatically reverse migrations or boot the old unrelated application against Lotline facts. Take a backup before future schema changes, and prefer a forward correction over destructive rollback.
