-- Allow the new EXPIRED ride status (no driver accepted within the dispatch window).
ALTER TABLE rides DROP CONSTRAINT chk_rides_status;
ALTER TABLE rides ADD CONSTRAINT chk_rides_status
    CHECK (status IN ('SCHEDULED', 'REQUESTED', 'MATCHED', 'IN_PROGRESS',
                      'COMPLETED', 'CANCELLED', 'EXPIRED'));
