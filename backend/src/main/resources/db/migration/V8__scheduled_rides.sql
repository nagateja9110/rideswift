-- RideSwift Phase 8 — scheduled (book-for-later) rides. A ride with a future
-- scheduled_at is parked in SCHEDULED status and dispatched to matching by a
-- Quartz sweeper when its time arrives.
ALTER TABLE rides ADD COLUMN scheduled_at TIMESTAMPTZ;

-- Allow the new SCHEDULED status.
ALTER TABLE rides DROP CONSTRAINT chk_rides_status;
ALTER TABLE rides ADD CONSTRAINT chk_rides_status
    CHECK (status IN ('SCHEDULED', 'REQUESTED', 'MATCHED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

-- The sweeper queries (status, scheduled_at).
CREATE INDEX ix_rides_scheduled ON rides (status, scheduled_at);
