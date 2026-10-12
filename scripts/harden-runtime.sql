-- Run with psql as the migration/schema owner after Flyway has bootstrapped.
-- psql -v runtime_password=<secret> -f scripts/harden-runtime.sql
-- Never use shell tracing or paste the password into committed commands.
CREATE ROLE lotline_runtime LOGIN PASSWORD :'runtime_password';
SELECT format('GRANT CONNECT ON DATABASE %I TO lotline_runtime', current_database()) \gexec
GRANT USAGE ON SCHEMA lotline TO lotline_runtime;
GRANT SELECT,INSERT ON ALL TABLES IN SCHEMA lotline TO lotline_runtime;
REVOKE ALL ON lotline.lotline_schema_history FROM lotline_runtime;
GRANT UPDATE,DELETE ON lotline.workspaces,lotline.organizations,lotline.sites,
 lotline.memberships,lotline.membership_sites,lotline.invitations,lotline.invitation_sites,
 lotline.auth_sessions,lotline.used_refresh_tokens,lotline.transfers,lotline.transfer_lines,
 lotline.discrepancies,lotline.balances,lotline.recalls,lotline.recall_lots,lotline.recall_targets,
 lotline.outbox,lotline.inbox TO lotline_runtime;
-- Future migrations must explicitly extend grants when adding a projection.
-- No UPDATE, DELETE, TRUNCATE, schema ownership or trigger alteration on fact tables.
