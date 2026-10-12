# Security and sharing

The tenant boundary is a cooperating **workspace**. Directory membership intentionally shares organization/site names. It does not share every partner’s stock or internal operational claims.

| Grant | Scope |
|---|---|
| OWNER | Administers its new workspace, network stock and permission grants. Cannot manufacture another organization’s receiver assertion. |
| OPERATOR | Operates its organization and granted sites; no unrestricted partner holdings. |
| PARTNER_OPERATOR | Its own authorized holdings / handoffs / incident targets, relevant ancestry, receiving and permitted onward stock commands. |
| AUDITOR | Read-only operational scope; an explicit `networkRead` grant may broaden reads across the workspace. Personal inbox read state is permitted. |
| RECALL_MANAGER | Explicit operator permission for full incident scope, activation / closure and accountable loss. Site operations remain within the membership’s operational scope. |

An empty site list grants all sites **in the member’s own organization**, not every network site. Read/write/trace/event/export/notification checks apply site restrictions. Composite domain foreign keys include workspace identity. A token carries user/session identity, not an authoritative role. Each request loads the current membership; commands recheck it after acquiring the workspace guard. Forged body fields are rejected; a mismatched workspace header is forbidden. Request context/MDC is cleared in `finally`; workers take explicit workspace IDs.

## Authentication

BCrypt cost 12 stores password hashes. A new signup creates its own owner workspace; existing-workspace ownership cannot be supplied by a client. Login has a bounded in-process per-account attempt gate; it is not a distributed perimeter defense.

Access JWTs expire after 15 minutes and stay in browser memory. Each device/session has a separate hashed refresh secret in PostgreSQL, 14-day sliding expiry, rotation, revocation and used-token replay detection. A replay revokes that session. Refresh credentials never appear in JSON. The client uses single-flight refresh and browser Web Locks where available to serialize cookie rotation across tabs.

Production refresh cookies are `__Host-lotline-refresh; HttpOnly; Secure; Path=/; SameSite=Strict`, without a Domain attribute. Local HTTP uses a separately named non-Secure cookie. Cookie auth POSTs require an exact trusted Origin. Credentialed CORS has no wildcard. Logout revokes the session and deletes the same cookie path/name. Validate these through the actual deployed frontend proxy, not just localhost.

Invitation links are email/workspace/organization/role-bound, single-use and expire after seven days. High-entropy HMAC-derived token bytes are never stored as a reversible raw token; only their hash is stored. The owner can reconstruct the same invitation response for a persisted command without storing the link secret. Signing-secret rotation invalidates outstanding invitations. Onboarding request digests are keyed, so a database-only digest is not a fast offline password verifier.

API responses are private/no-store. Pending command **keys and content hashes**, scoped to user/workspace, can survive a timeout/reload in sessionStorage; access and refresh tokens cannot. Browser data is not authoritative. Deterministic failure responses roll back; uncertain transport failures retain their key for retry.

## Database privileges and integrity

`scripts/harden-runtime.sql` grants a dedicated runtime role SELECT/INSERT and only the necessary projection/domain UPDATE/DELETE. It grants no fact rewriting, TRUNCATE, schema ownership or trigger alteration. Run it as the migration owner with a psql secret variable, then configure:

- Runtime credentials in `DATABASE_URL` (or `DB_*`).
- Migration owner in `MIGRATION_DB_USER` / `MIGRATION_DB_PASSWORD`.

The temporary Blueprint initially uses the generated database owner for bootstrap; append-only triggers still reject normal rewrites. For durable use, split privileges before accepting real operational records. Future migrations must extend runtime grants deliberately. Do not alter legacy `public` privileges or data as part of this cutover.

Never commit `.env`, dump credentials, enable request-body / JDBC parameter logging around auth, or put private secrets in `VITE_*`. No binary uploads or local-disk evidence are used. Store backups/export packets privately: they contain network assertions and actor information.

This product does not establish legal compliance, certify product authenticity, or verify that a partner’s physical assertion is true.
