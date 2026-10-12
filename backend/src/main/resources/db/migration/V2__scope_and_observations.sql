ALTER TABLE memberships ADD COLUMN network_read boolean NOT NULL DEFAULT false;
ALTER TABLE invitations ADD COLUMN network_read boolean NOT NULL DEFAULT false;
CREATE TABLE event_sites (workspace_id uuid NOT NULL, event_id uuid NOT NULL, site_id uuid NOT NULL, PRIMARY KEY(workspace_id,event_id,site_id),
 FOREIGN KEY(workspace_id,event_id) REFERENCES events(workspace_id,id), FOREIGN KEY(workspace_id,site_id) REFERENCES sites(workspace_id,id));
-- Scope tags are derived from accepted journal/command site references, not new physical facts.
INSERT INTO event_sites SELECT DISTINCT workspace_id,event_id,site_id FROM inventory_journal;
INSERT INTO event_sites SELECT e.workspace_id,e.id,s.id FROM events e JOIN sites s ON s.workspace_id=e.workspace_id AND (s.id::text=e.payload->>'sourceSiteId' OR s.id::text=e.payload->>'destinationSiteId') ON CONFLICT DO NOTHING;
CREATE TABLE custody_observations (workspace_id uuid NOT NULL, id uuid NOT NULL, lot_id uuid NOT NULL, quantity numeric(20,3) NOT NULL CHECK(quantity>0),
 reason text NOT NULL, evidence text NOT NULL, sequence bigint NOT NULL, actor_id uuid NOT NULL REFERENCES users(id), PRIMARY KEY(workspace_id,id), FOREIGN KEY(workspace_id,lot_id) REFERENCES lots(workspace_id,id));
CREATE TABLE observation_resolutions (workspace_id uuid NOT NULL, id uuid NOT NULL, observation_id uuid NOT NULL, reason text NOT NULL, evidence text NOT NULL, sequence bigint NOT NULL, actor_id uuid NOT NULL REFERENCES users(id),
 PRIMARY KEY(workspace_id,id), UNIQUE(workspace_id,observation_id), FOREIGN KEY(workspace_id,observation_id) REFERENCES custody_observations(workspace_id,id));
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON event_sites FOR EACH ROW EXECUTE FUNCTION reject_fact_rewrite();
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON custody_observations FOR EACH ROW EXECUTE FUNCTION reject_fact_rewrite();
CREATE TRIGGER append_only BEFORE UPDATE OR DELETE ON observation_resolutions FOR EACH ROW EXECUTE FUNCTION reject_fact_rewrite();
