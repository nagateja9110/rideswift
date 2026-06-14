-- RideSwift Phase 8 (tipping) — optional gratuity added to a ride payment.
-- The charged `amount` already includes the tip; `tip_amount` records the gratuity portion.
ALTER TABLE payments ADD COLUMN tip_amount NUMERIC(10, 2) NOT NULL DEFAULT 0;
