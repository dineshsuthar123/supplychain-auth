# Architecture

One Spring Boot API owns identity, workspace, catalog, inventory, custody, lineage, recall, audit and internal notifications. Feature services contain domain rules. Controllers accept explicit command records, never persistence entities. JDBC uses the `lotline` search path; Flyway owns `lotline.lotline_schema_history`.

## Vocabulary and invariants

| Fact | Meaning |
|---|---|
| Lot genealogy | Consumed input lots contributed to newly identified output lots. Moving a shipment does not create lineage. |
| Custody | The same lot and exact quantity move between source / destination sites and organizations. Actor identity is separate from either party. |
| Holding | Current quantity at a site in a named stock status. `CONSUMED` is historical input, excluded from exposure. |
| Observation | A human report of unknown custody; it does not add commissioned inventory. |
| Acknowledgment | An organization received an incident notice. Recovery requires a separate physical quantity command. |
| Aggregation | Packing retained lot identities into a container; no aggregation workflow is claimed in v1. |

UUIDs are workspace-qualified. External codes are unique by **workspace + issuer organization + product + code**. Quantities are `numeric(20,3)` in PostgreSQL and decimal strings in JSON; inputs have at most 17 whole digits / 3 fractional digits. There is no rounding or inferred conversion. Transformations require the same product/base unit and exact `outputs + declared loss = consumed inputs`. Outputs are new UUID lots, so command-created ancestry cannot cycle. Expiry propagates conservatively; expired lots cannot dispatch.

## Command transaction

Every domain mutation follows this order:

1. Resolve session membership server-side, then lock its workspace row `FOR UPDATE`.
2. Recheck the permission grant / session, persistent actor + command-scope + key digest, entity version, stock and active recall holds.
3. Allocate the next workspace sequence under that same guard; append the accepted event and journal debits / credits.
4. Update domain/read projections and enqueue recipient notifications in the same transaction.
5. Persist the accepted response. Any failure rolls back the sequence, facts, quantities and outbox together.

The workspace guard intentionally serializes stock/genealogy/recall writes. It makes competing dispatches, duplicate receipts and recall activation race-safe, at the cost of per-workspace write throughput. Inventory debits update an existing balance; PostgreSQL checks a proposed negative insert before UPSERT conflict handling. All iteration / target ordering is deterministic. Failed commands are not cached as accepted; retrying after correcting the state is valid.

## Custody and incidents

A draft may reserve stock. Dispatch moves available / reserved stock into a transfer-bound `IN_TRANSIT` balance. Only the destination organization may assert a receipt. A 500 / 480 receipt creates 480 destination stock plus 20 `UNRESOLVED` transfer stock. Later received / returned / authorized accountable loss are separately recorded resolutions. Dispatched cancellation enters reconciliation; it never restores source stock silently.

The destination site on transfer-bound in-transit or unresolved rows identifies the receiving responsibility. It does not assert that those units physically arrived. Both authorized shipment parties can inspect those unresolved claims; normal received destination stock remains within destination scope.

Recall preview traverses known descendant edges with visited-lot deduplication and operation paths. Mixed outputs are entirely potentially affected. Roots in one incident must share a base unit; unrelated totals are expressed in that unit. Traversal bounds yield an explicit incomplete scope that cannot activate.

Activation checks the preview sequence under the shared guard and commits effective holds synchronously. Reservations are rechecked at dispatch. Affected incoming stock can be received into `HELD`. Later corrections / receipts revise targets and preserve earlier revisions; new affected evidence reopens closed incidents. Overlapping active recalls continue to block normal stock commands after one closes.

Closure requires an independent response from each current target organization, no available / reserved / held / in-transit / unresolved affected quantity, and no open reported evidence gap. Recovered, disposed and authorized accountable loss satisfy quantity accounting; acknowledgment alone does not. Operators’ assertions remain visible with their actor and references.

## Read models, history and notifications

Trace uses a repeatable-read snapshot: lot creation and lineage are filtered at sequence N; holdings are summed from journal entries at N. Reported `occurred_at` and server `recorded_at` remain separate. Projections can be rebuilt and compared with the journal. Events, lots, lineage, operations, receipts, resolutions, acknowledgments, dispositions, observations and scope revisions reject UPDATE / DELETE.

The outbox claims at most 50 due rows using `FOR UPDATE SKIP LOCKED`, a 30-second lease, attempt count and bounded backoff. Creating a local inbox message and marking delivery complete share one transaction. Recipient/event uniqueness prevents duplicates; expired leases recover after restart. After 12 failures, a row remains visible for operational investigation rather than retrying forever. No external delivery is implemented. Holds never wait for this worker.

An export is a bounded, scoped repeatable-read ZIP: events, journal, graph, incident revisions, current exposure, responses, printable HTML and a hash manifest. Verification detects changed bytes; it is neither physical verification nor an external trust anchor.
