-- Run only against the isolated synthetic fixture created by load-test.py.
\set ON_ERROR_STOP on
SET search_path=lotline;
SELECT id AS fixture_workspace, sequence AS fixture_sequence FROM workspaces
 WHERE name='SYNTHETIC benchmark workspace' ORDER BY created_at DESC LIMIT 1 \gset
-- Use the separate coffee fixture for a non-empty two-child descendant lookup.
SELECT l.workspace_id AS genealogy_workspace,l.input_lot_id AS genealogy_root
 FROM lineage l WHERE l.workspace_id=(SELECT id FROM workspaces WHERE name LIKE 'Meridian coffee%'
 ORDER BY created_at LIMIT 1) GROUP BY l.workspace_id,l.input_lot_id
 ORDER BY count(*) DESC,l.input_lot_id LIMIT 1 \gset
\echo Journal snapshot aggregation
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)
 SELECT lot_id,site_id,status,transfer_id,sum(delta) FROM inventory_journal
 WHERE workspace_id=:'fixture_workspace' AND sequence<=:fixture_sequence
 GROUP BY lot_id,site_id,status,transfer_id HAVING sum(delta)>0;
\echo Descendant lookup
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)
 SELECT output_lot_id,operation_id FROM lineage
 WHERE workspace_id=:'genealogy_workspace' AND input_lot_id=:'genealogy_root';
\echo Bounded accepted timeline
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)
 SELECT sequence,type,occurred_at,recorded_at FROM events
 WHERE workspace_id=:'fixture_workspace' AND sequence<=:fixture_sequence
 ORDER BY sequence DESC LIMIT 200;
\echo Bounded due outbox selection (without claiming)
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF)
 SELECT id,workspace_id FROM outbox WHERE delivered_at IS NULL
 AND next_attempt_at<=now() AND (lease_until IS NULL OR lease_until<now())
 ORDER BY next_attempt_at LIMIT 50;
