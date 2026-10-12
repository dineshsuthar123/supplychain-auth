# Synthetic coffee case

Set `DEMO_PASSWORD` in your terminal; no owner password is hardcoded or seeded on application startup. Use an isolated workspace and a fresh prefix for each completed walkthrough. Local demo account emails are `<prefix>+owner@example.test` and `<prefix>+partner1` through `partner4@example.test`.

```sh
python scripts/seed-demo.py --prefix lotline-demo
```

This leaves a recorded case ready for scope preview:

| Accepted command | Current consequence |
|---|---|
| Commission CF-2401 / 1,200 | Manufacturer stock |
| Split into A / 700 and B / 500 | Parent consumed; two newly identified lots |
| Split A into A1 / 350 and A2 / 350 | Explicit two-level genealogy |
| Dispatch / receive A1 and A2 | 350 at each of two partner sites |
| Dispatch B / 500, receive / 480 | 480 at third site; 20 unresolved and transfer-bound |
| Dispatch / receive CF-2402 / 200 | Fourth site, known unrelated stock |

Sign in as the owner and select CF-2401 in Trace. Review its contribution edges and current holding branches. Open recall scope, enter the packaging-defect reason and preview: **1,180 recoverable + 20 unresolved**, with the unrelated **200** outside known scope. Review the sequence/sites and deliberately confirm activation.

Use separate partner browser sessions (or sign out/sign in) to inspect each target. Affected handoff / transformation attempts return `RECALL_HOLD`; the relevant incident is identified. Partners acknowledge notice independently, then record 350 / 350 / 480 physical recovery with a reason/register reference. An acknowledgment alone changes no recovery quantity.

The owner’s close attempt stays blocked by the unresolved 20. Open B’s handoff, preserve both sender/receiver claims, and record an authorized accountable loss with investigation evidence. Close with the quantity/response justification. Export the packet and run `python scripts/verify-evidence.py <zip>`. Reload/restart to inspect the same accepted sequences and quantities.

For the complete automatic real-API walkthrough:

```sh
python scripts/smoke.py --output artifacts/smoke
python scripts/verify-evidence.py artifacts/smoke/lotline-evidence.zip
```

Smoke uses a unique prefix, checks blocked affected actions and permitted unaffected onward movement, and verifies the live projection. Never complete the same fixture twice as if it were fresh stock; use a new prefix. Fixed domain command keys make preparation retries safe, but terminal stock stays terminal.

The PostgreSQL suite separately covers merge/commingling, diamond contribution paths, partial input consumption, active-hold receipt, late correction/reopening, overlapping incidents, unknown-custody observations, returns without ancestry, and lease recovery. Browser E2E records the full case through actual forms and four independently authenticated partners.

The public ribbon walkthrough is clearly synthetic and remains usable without a backend. Authenticated workspaces never switch into sample data during an outage.
