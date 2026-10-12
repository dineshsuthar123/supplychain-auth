# API guide

Contract: [OpenAPI 3.1](openapi.json). Its command DTOs generate `frontend/src/lib/api.generated.ts`; run `python scripts/generate-api.py --check` to detect drift. Read DTOs used by screens are in `frontend/src/lib/types.ts`. Integration tests exercise real HTTP decoding and reject numeric quantity JSON / arbitrary signup role fields.

All browser calls use same-origin `/api/v1`. Login/register/invitation acceptance return an in-memory access token and set the refresh cookie; refresh/logout require a trusted Origin. Use bearer authorization for operational calls. `/auth/workspace` switches only to an existing granted membership. No body workspace ID is accepted by domain commands.

| Workflow | Endpoints |
|---|---|
| Identity | `POST /auth/register`, `/login`, `/accept-invitation`, `/refresh`, `/logout`, `/workspace`; `GET /auth/me` |
| Setup | `GET /workspace`; owner `POST /workspace/organizations`, `/sites`, `/invitations`, `/permissions`; `GET/POST /products` |
| Inventory | `POST /inventory/commission`, `/operations`, `/dispositions`, `/corrections`; `GET /inventory/holdings` |
| Trace | `GET /trace?sequence=N`; `GET /events?after=N&limit=100` |
| Custody | `GET/POST /transfers`; `GET /transfers/{id}`; `POST /{id}/dispatch`, `/receive`, `/resolve`, `/cancel` |
| Incident | `GET /recalls`, `/{id}`; `POST /preview`, `/recalls`, `/{id}/acknowledge`, `/close`, `/reopen` |
| Evidence | `GET /evidence/export?sequence=N`, `/projection-check`; `POST /evidence/rebuild`; `GET/POST /observations`, `POST /observations/{id}/resolve` |
| Inbox / health | `GET /inbox`, `POST /inbox/{id}/read`; `/health`; `/actuator/health/liveness`, `/actuator/health/readiness` |

## Exact commands

```json
{
  "kind": "SPLIT",
  "siteId": "authorized-site-uuid",
  "inputs": [{"lotId": "input-lot-uuid", "quantity": "1200"}],
  "outputs": [{"code": "CF-2401-A", "quantity": "700"}, {"code": "CF-2401-B", "quantity": "500"}],
  "declaredLoss": "0"
}
```

UUID placeholders above must be replaced with accepted IDs. Every significant domain mutation requires `Idempotency-Key: <8–128 allowed characters>`. The key is stored under workspace + actor + command scope with a digest and accepted response. Same key/body returns the original accepted business result, including simultaneous requests/restarts. Changed content returns 409. A failed command rolls back and has no accepted idempotency record. Registration keeps one created workspace per onboarding key; authentication credentials themselves rotate into a fresh session rather than storing raw refresh tokens for replay.

Receipt lines report exact decimal `quantity`, `GOOD` / `DAMAGED` / `HELD`, and an exception reason where necessary. The destination operator supplies the transfer’s current `version`. Over-receipt is rejected. Resolution identifies the discrepancy and a separately authorized `LATER_RECEIVED`, `RETURNED`, or `ACCOUNTABLE_LOSS` disposition.

Recall activation includes roots, title/reason, `previewSequence`, and explicit `confirmed: true`. A stale preview returns 409 `STALE_PREVIEW` with the updated scope in `details`; review and confirm again. Preview has `complete`, explanatory paths, current exposures, terminal quantities, known unrelated stock, base unit and visible gaps. Mixed output is wholly potentially affected.

## Stable errors and bounds

Errors contain `code`, actionable `message`, optional `details`, and a trace ID. Examples: `SCOPE_DENIED`, `RECEIVER_REQUIRED`, `QUANTITY_PRECISION`, `CONSERVATION_FAILED`, `INSUFFICIENT_STOCK`, `RECALL_HOLD`, `EXPIRED_LOT`, `TRANSFER_STATE`, `OVER_RECEIPT`, `STALE_ENTITY`, `STALE_PREVIEW`, `IDEMPOTENCY_CONFLICT`, `CLOSURE_BLOCKED`, `MEMBERSHIP_CHANGED`, `INCOMPLETE_SCOPE`, `BACKEND_WARMING`, `AUTH_RATE_LIMIT`.

Graph: at most 2,000 lots / 10,000 lineage edges / 5,000 holding rows, with a completeness marker. Recall preview: 2,000 affected lots / 10,000 edges / 10,000 exposure rows; incomplete activation is blocked. Event pages cap at 200. Export caps at 20,000 events / 50,000 journal entries and refuses incomplete graph export. Transfers/catalog/inbox lists have explicit bounds. Large networks need scoped/paginated expansion before exceeding these first-release bounds.

Historical mode means **what was accepted by committed sequence N**, not a retroactive occurred-at story. Corrected facts appear at their newly accepted sequence. Mutation DTOs do not accept historical sequence overrides.
