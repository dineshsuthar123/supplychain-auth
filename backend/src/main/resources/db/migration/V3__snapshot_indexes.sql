CREATE INDEX command_event_lookup ON idempotency_records(workspace_id,command_id);
CREATE INDEX event_type_snapshot ON events(workspace_id,type,sequence);
