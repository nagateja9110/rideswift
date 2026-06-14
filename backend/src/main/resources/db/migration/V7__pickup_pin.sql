-- RideSwift Phase 7 — PIN-verified pickup. A 4-digit PIN is generated when a driver
-- is matched; the passenger shows it and the driver must enter it to start the trip.
ALTER TABLE rides ADD COLUMN pickup_pin VARCHAR(6);
