CREATE TABLE users (
 id uuid PRIMARY KEY, email text NOT NULL UNIQUE, name text NOT NULL,
 password_hash text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE workspaces (id uuid PRIMARY KEY, name text NOT NULL, sequence bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE organizations (workspace_id uuid NOT NULL REFERENCES workspaces(id), id uuid NOT NULL, name text NOT NULL, PRIMARY KEY(workspace_id,id));
CREATE TABLE sites (workspace_id uuid NOT NULL, id uuid NOT NULL, organization_id uuid NOT NULL, name text NOT NULL,
 PRIMARY KEY(workspace_id,id), UNIQUE(workspace_id,id,organization_id), FOREIGN KEY(workspace_id,organization_id) REFERENCES organizations(workspace_id,id));
CREATE TABLE memberships (workspace_id uuid NOT NULL, id uuid NOT NULL, user_id uuid NOT NULL REFERENCES users(id), organization_id uuid NOT NULL,
 role text NOT NULL CHECK(role IN ('OWNER','OPERATOR','PARTNER_OPERATOR','AUDITOR')), recall_manager boolean NOT NULL DEFAULT false,
 PRIMARY KEY(workspace_id,id), UNIQUE(workspace_id,user_id), FOREIGN KEY(workspace_id,organization_id) REFERENCES organizations(workspace_id,id));
CREATE TABLE membership_sites (workspace_id uuid NOT NULL, membership_id uuid NOT NULL, site_id uuid NOT NULL,
 PRIMARY KEY(workspace_id,membership_id,site_id), FOREIGN KEY(workspace_id,membership_id) REFERENCES memberships(workspace_id,id), FOREIGN KEY(workspace_id,site_id) REFERENCES sites(workspace_id,id));
CREATE TABLE invitations (workspace_id uuid NOT NULL, id uuid NOT NULL, organization_id uuid NOT NULL, email text NOT NULL, role text NOT NULL CHECK(role IN ('OPERATOR','PARTNER_OPERATOR','AUDITOR')),
 recall_manager boolean NOT NULL DEFAULT false, token_hash text NOT NULL UNIQUE, expires_at timestamptz NOT NULL, used_at timestamptz,
 PRIMARY KEY(workspace_id,id), FOREIGN KEY(workspace_id,organization_id) REFERENCES organizations(workspace_id,id));
CREATE TABLE invitation_sites (workspace_id uuid NOT NULL, invitation_id uuid NOT NULL, site_id uuid NOT NULL,
 PRIMARY KEY(workspace_id,invitation_id,site_id), FOREIGN KEY(workspace_id,invitation_id) REFERENCES invitations(workspace_id,id), FOREIGN KEY(workspace_id,site_id) REFERENCES sites(workspace_id,id));
CREATE TABLE auth_sessions (id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id), workspace_id uuid NOT NULL,
 refresh_hash text NOT NULL UNIQUE, expires_at timestamptz NOT NULL, revoked_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(workspace_id,user_id) REFERENCES memberships(workspace_id,user_id));
CREATE TABLE used_refresh_tokens (token_hash text PRIMARY KEY, session_id uuid NOT NULL REFERENCES auth_sessions(id), used_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE onboarding_commands (email text NOT NULL, key text NOT NULL, digest text NOT NULL, user_id uuid NOT NULL REFERENCES users(id), workspace_id uuid NOT NULL REFERENCES workspaces(id), PRIMARY KEY(email,key));
CREATE TABLE products (workspace_id uuid NOT NULL REFERENCES workspaces(id), id uuid NOT NULL, name text NOT NULL, base_unit text NOT NULL CHECK(base_unit IN ('unit','kg','g','l','ml')),
 PRIMARY KEY(workspace_id,id));
CREATE TABLE lots (workspace_id uuid NOT NULL, id uuid NOT NULL, product_id uuid NOT NULL, issuer_id uuid NOT NULL, code text NOT NULL,
 issued_on date NOT NULL, expires_on date, created_sequence bigint NOT NULL,
 PRIMARY KEY(workspace_id,id), UNIQUE(workspace_id,issuer_id,product_id,code),
 FOREIGN KEY(workspace_id,product_id) REFERENCES products(workspace_id,id), FOREIGN KEY(workspace_id,issuer_id) REFERENCES organizations(workspace_id,id));
CREATE TABLE operations (workspace_id uuid NOT NULL REFERENCES workspaces(id), id uuid NOT NULL, kind text NOT NULL CHECK(kind IN ('SPLIT','MERGE','REPACKAGE')),
 site_id uuid NOT NULL, declared_loss numeric(20,3) NOT NULL CHECK(declared_loss>=0), sequence bigint NOT NULL, PRIMARY KEY(workspace_id,id), FOREIGN KEY(workspace_id,site_id) REFERENCES sites(workspace_id,id));
CREATE TABLE operation_inputs (workspace_id uuid NOT NULL, operation_id uuid NOT NULL, lot_id uuid NOT NULL, quantity numeric(20,3) NOT NULL CHECK(quantity>0),
 PRIMARY KEY(workspace_id,operation_id,lot_id), FOREIGN KEY(workspace_id,operation_id) REFERENCES operations(workspace_id,id), FOREIGN KEY(workspace_id,lot_id) REFERENCES lots(workspace_id,id));
CREATE TABLE operation_outputs (workspace_id uuid NOT NULL, operation_id uuid NOT NULL, lot_id uuid NOT NULL, quantity numeric(20,3) NOT NULL CHECK(quantity>0),
 PRIMARY KEY(workspace_id,operation_id,lot_id), FOREIGN KEY(workspace_id,operation_id) REFERENCES operations(workspace_id,id), FOREIGN KEY(workspace_id,lot_id) REFERENCES lots(workspace_id,id));
CREATE TABLE lineage (workspace_id uuid NOT NULL, input_lot_id uuid NOT NULL, output_lot_id uuid NOT NULL, operation_id uuid NOT NULL, sequence bigint NOT NULL,
 PRIMARY KEY(workspace_id,input_lot_id,output_lot_id), CHECK(input_lot_id<>output_lot_id), FOREIGN KEY(workspace_id,input_lot_id) REFERENCES lots(workspace_id,id),
 FOREIGN KEY(workspace_id,output_lot_id) REFERENCES lots(workspace_id,id), FOREIGN KEY(workspace_id,operation_id) REFERENCES operations(workspace_id,id));
CREATE INDEX lineage_output ON lineage(workspace_id,output_lot_id);
CREATE TABLE transfers (workspace_id uuid NOT NULL, id uuid NOT NULL, source_site_id uuid NOT NULL, destination_site_id uuid NOT NULL,
 source_organization_id uuid NOT NULL, destination_organization_id uuid NOT NULL,
 status text NOT NULL CHECK(status IN ('DRAFT','DISPATCHED','RECEIVED','DISPUTED','CANCELLED','RECONCILED')), reference text NOT NULL,
 version bigint NOT NULL DEFAULT 0, created_sequence bigint NOT NULL, PRIMARY KEY(workspace_id,id),
 FOREIGN KEY(workspace_id,source_site_id,source_organization_id) REFERENCES sites(workspace_id,id,organization_id),
 FOREIGN KEY(workspace_id,destination_site_id,destination_organization_id) REFERENCES sites(workspace_id,id,organization_id));
CREATE INDEX transfer_parties ON transfers(workspace_id,destination_organization_id,status);
CREATE INDEX transfer_source ON transfers(workspace_id,source_organization_id,status);
CREATE TABLE transfer_lines (workspace_id uuid NOT NULL, transfer_id uuid NOT NULL, lot_id uuid NOT NULL, quantity numeric(20,3) NOT NULL CHECK(quantity>0), reserved boolean NOT NULL DEFAULT false,
 PRIMARY KEY(workspace_id,transfer_id,lot_id), FOREIGN KEY(workspace_id,transfer_id) REFERENCES transfers(workspace_id,id), FOREIGN KEY(workspace_id,lot_id) REFERENCES lots(workspace_id,id));
CREATE TABLE receipts (workspace_id uuid NOT NULL, id uuid NOT NULL, transfer_id uuid NOT NULL, lot_id uuid NOT NULL, received_quantity numeric(20,3) NOT NULL CHECK(received_quantity>=0),
 condition text NOT NULL CHECK(condition IN ('GOOD','DAMAGED','HELD')), reason text NOT NULL, actor_id uuid NOT NULL REFERENCES users(id), sequence bigint NOT NULL,
 PRIMARY KEY(workspace_id,id), UNIQUE(workspace_id,transfer_id,lot_id), FOREIGN KEY(workspace_id,transfer_id,lot_id) REFERENCES transfer_lines(workspace_id,transfer_id,lot_id));
CREATE TABLE discrepancies (workspace_id uuid NOT NULL, id uuid NOT NULL, transfer_id uuid NOT NULL, lot_id uuid NOT NULL, sent_quantity numeric(20,3) NOT NULL,
 received_quantity numeric(20,3) NOT NULL, unresolved_quantity numeric(20,3) NOT NULL CHECK(unresolved_quantity>=0), PRIMARY KEY(workspace_id,id),
 UNIQUE(workspace_id,transfer_id,lot_id), FOREIGN KEY(workspace_id,transfer_id,lot_id) REFERENCES transfer_lines(workspace_id,transfer_id,lot_id));
CREATE TABLE discrepancy_resolutions (workspace_id uuid NOT NULL, id uuid NOT NULL, discrepancy_id uuid NOT NULL, disposition text NOT NULL CHECK(disposition IN ('LATER_RECEIVED','RETURNED','ACCOUNTABLE_LOSS')),
 quantity numeric(20,3) NOT NULL CHECK(quantity>0), reason text NOT NULL, evidence text NOT NULL, actor_id uuid NOT NULL REFERENCES users(id), sequence bigint NOT NULL,
 PRIMARY KEY(workspace_id,id), FOREIGN KEY(workspace_id,discrepancy_id) REFERENCES discrepancies(workspace_id,id));
CREATE TABLE events (workspace_id uuid NOT NULL REFERENCES workspaces(id), id uuid NOT NULL, sequence bigint NOT NULL, actor_id uuid NOT NULL REFERENCES users(id), organization_id uuid NOT NULL,
 command_id uuid NOT NULL, type text NOT NULL, schema_version integer NOT NULL DEFAULT 1, payload jsonb NOT NULL,
 recorded_at timestamptz NOT NULL DEFAULT now(), occurred_at timestamptz NOT NULL, PRIMARY KEY(workspace_id,id), UNIQUE(workspace_id,sequence),
 FOREIGN KEY(workspace_id,organization_id) REFERENCES organizations(workspace_id,id));
CREATE TABLE event_audience (workspace_id uuid NOT NULL, event_id uuid NOT NULL, organization_id uuid NOT NULL,
 PRIMARY KEY(workspace_id,event_id,organization_id), FOREIGN KEY(workspace_id,event_id) REFERENCES events(workspace_id,id), FOREIGN KEY(workspace_id,organization_id) REFERENCES organizations(workspace_id,id));
CREATE TABLE inventory_journal (workspace_id uuid NOT NULL, id uuid NOT NULL, event_id uuid NOT NULL, sequence bigint NOT NULL, lot_id uuid NOT NULL, site_id uuid NOT NULL,
 status text NOT NULL CHECK(status IN ('AVAILABLE','RESERVED','IN_TRANSIT','UNRESOLVED','HELD','RECOVERED','DISPOSED','LOST','CONSUMED')), transfer_id uuid,
 delta numeric(20,3) NOT NULL CHECK(delta<>0), PRIMARY KEY(workspace_id,id),
 FOREIGN KEY(workspace_id,event_id) REFERENCES events(workspace_id,id), FOREIGN KEY(workspace_id,lot_id) REFERENCES lots(workspace_id,id),
 FOREIGN KEY(workspace_id,site_id) REFERENCES sites(workspace_id,id), FOREIGN KEY(workspace_id,transfer_id) REFERENCES transfers(workspace_id,id));
CREATE INDEX journal_snapshot ON inventory_journal(workspace_id,sequence);
CREATE INDEX journal_lot ON inventory_journal(workspace_id,lot_id,site_id,status);
CREATE TABLE balances (workspace_id uuid NOT NULL, lot_id uuid NOT NULL, site_id uuid NOT NULL, status text NOT NULL, transfer_id uuid, quantity numeric(20,3) NOT NULL CHECK(quantity>=0),
 UNIQUE NULLS NOT DISTINCT(workspace_id,lot_id,site_id,status,transfer_id),
 FOREIGN KEY(workspace_id,lot_id) REFERENCES lots(workspace_id,id), FOREIGN KEY(workspace_id,site_id) REFERENCES sites(workspace_id,id),
 FOREIGN KEY(workspace_id,transfer_id) REFERENCES transfers(workspace_id,id));
CREATE INDEX balance_site ON balances(workspace_id,site_id,status);
CREATE TABLE recalls (workspace_id uuid NOT NULL, id uuid NOT NULL, title text NOT NULL, reason text NOT NULL, roots uuid[] NOT NULL,
 status text NOT NULL CHECK(status IN ('ACTIVE','CLOSED')), activation_sequence bigint NOT NULL, version bigint NOT NULL DEFAULT 1, closed_sequence bigint,
 PRIMARY KEY(workspace_id,id), FOREIGN KEY(workspace_id) REFERENCES workspaces(id));
CREATE TABLE recall_lots (workspace_id uuid NOT NULL, recall_id uuid NOT NULL, lot_id uuid NOT NULL,
 PRIMARY KEY(workspace_id,recall_id,lot_id), FOREIGN KEY(workspace_id,recall_id) REFERENCES recalls(workspace_id,id), FOREIGN KEY(workspace_id,lot_id) REFERENCES lots(workspace_id,id));
CREATE INDEX recall_lot_hold ON recall_lots(workspace_id,lot_id);
CREATE TABLE recall_revisions (workspace_id uuid NOT NULL, recall_id uuid NOT NULL, revision bigint NOT NULL, sequence bigint NOT NULL, scope jsonb NOT NULL, reason text NOT NULL,
 PRIMARY KEY(workspace_id,recall_id,revision), FOREIGN KEY(workspace_id,recall_id) REFERENCES recalls(workspace_id,id));
CREATE TABLE recall_targets (workspace_id uuid NOT NULL, id uuid NOT NULL, recall_id uuid NOT NULL, lot_id uuid NOT NULL, site_id uuid NOT NULL, transfer_id uuid,
 quantity numeric(20,3) NOT NULL CHECK(quantity>=0), PRIMARY KEY(workspace_id,id), UNIQUE NULLS NOT DISTINCT(workspace_id,recall_id,lot_id,site_id,transfer_id),
 FOREIGN KEY(workspace_id,recall_id) REFERENCES recalls(workspace_id,id), FOREIGN KEY(workspace_id,lot_id) REFERENCES lots(workspace_id,id),
 FOREIGN KEY(workspace_id,site_id) REFERENCES sites(workspace_id,id), FOREIGN KEY(workspace_id,transfer_id) REFERENCES transfers(workspace_id,id));
CREATE TABLE acknowledgments (workspace_id uuid NOT NULL, id uuid NOT NULL, recall_id uuid NOT NULL, organization_id uuid NOT NULL, actor_id uuid NOT NULL REFERENCES users(id),
 sequence bigint NOT NULL, note text NOT NULL, PRIMARY KEY(workspace_id,id), UNIQUE(workspace_id,recall_id,organization_id),
 FOREIGN KEY(workspace_id,recall_id) REFERENCES recalls(workspace_id,id), FOREIGN KEY(workspace_id,organization_id) REFERENCES organizations(workspace_id,id));
CREATE TABLE dispositions (workspace_id uuid NOT NULL, id uuid NOT NULL, lot_id uuid NOT NULL, site_id uuid NOT NULL, kind text NOT NULL CHECK(kind IN ('HOLD','RECOVER','DISPOSE','LOSS')),
 quantity numeric(20,3) NOT NULL CHECK(quantity>0), reason text NOT NULL, evidence text NOT NULL, actor_id uuid NOT NULL REFERENCES users(id), sequence bigint NOT NULL,
 PRIMARY KEY(workspace_id,id), FOREIGN KEY(workspace_id,lot_id) REFERENCES lots(workspace_id,id), FOREIGN KEY(workspace_id,site_id) REFERENCES sites(workspace_id,id));
CREATE TABLE idempotency_records (workspace_id uuid NOT NULL REFERENCES workspaces(id), actor_id uuid NOT NULL REFERENCES users(id), scope text NOT NULL, key text NOT NULL,
 request_digest text NOT NULL, response jsonb NOT NULL, status integer NOT NULL, command_id uuid NOT NULL, recorded_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(workspace_id,actor_id,scope,key));
CREATE TABLE outbox (workspace_id uuid NOT NULL, id uuid NOT NULL, event_id uuid NOT NULL, recipient_id uuid NOT NULL REFERENCES users(id), message text NOT NULL,
 attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT now(), lease_until timestamptz, delivered_at timestamptz, last_error text,
 PRIMARY KEY(workspace_id,id), UNIQUE(workspace_id,event_id,recipient_id), FOREIGN KEY(workspace_id,event_id) REFERENCES events(workspace_id,id));
CREATE INDEX outbox_due ON outbox(next_attempt_at) WHERE delivered_at IS NULL;
CREATE TABLE inbox (workspace_id uuid NOT NULL, id uuid NOT NULL, event_id uuid NOT NULL, recipient_id uuid NOT NULL REFERENCES users(id), message text NOT NULL,
 read_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(workspace_id,id), UNIQUE(workspace_id,event_id,recipient_id), FOREIGN KEY(workspace_id,event_id) REFERENCES events(workspace_id,id));
CREATE INDEX inbox_recipient ON inbox(workspace_id,recipient_id,created_at DESC);

CREATE FUNCTION reject_fact_rewrite() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Accepted Lotline facts are append-only; append a linked correction'; END;
$$;
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['events','event_audience','inventory_journal','lots','lineage','operations','operation_inputs','operation_outputs','receipts','discrepancy_resolutions','recall_revisions','acknowledgments','dispositions'] LOOP
 EXECUTE format('CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION reject_fact_rewrite()',t);
 END LOOP;
END $$;
