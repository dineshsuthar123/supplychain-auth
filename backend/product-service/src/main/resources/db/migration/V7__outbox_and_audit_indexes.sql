CREATE INDEX IF NOT EXISTS idx_outbox_pending_dispatch
    ON blockchain_outbox(status, attempts, created_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_verification_events_today
    ON verification_events(created_at DESC, verified);
