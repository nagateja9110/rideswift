-- ---------------------------------------------------------------------------
-- Switch pricing from USD to INR and support Razorpay order tracking.
-- ---------------------------------------------------------------------------

-- Realistic Chennai ride-hailing rates (₹). A ~5 km / 6 min Economy trip ≈ ₹129.
UPDATE fare_rules SET base_fare = 50.00, per_km_rate = 14.00, per_minute_rate = 1.50
    WHERE vehicle_type = 'ECONOMY';
UPDATE fare_rules SET base_fare = 100.00, per_km_rate = 22.00, per_minute_rate = 2.50
    WHERE vehicle_type = 'PREMIUM';
UPDATE fare_rules SET base_fare = 80.00, per_km_rate = 18.00, per_minute_rate = 2.00
    WHERE vehicle_type = 'XL';

-- Convert existing demo history (seeded in USD magnitudes) to ₹ so dashboards/
-- earnings stay coherent. ~83 ₹/$ is fine for demo data.
UPDATE rides SET estimated_fare = ROUND(estimated_fare * 83, 2) WHERE estimated_fare IS NOT NULL;
UPDATE rides SET actual_fare = ROUND(actual_fare * 83, 2) WHERE actual_fare IS NOT NULL;
UPDATE payments SET amount = ROUND(amount * 83, 2),
                    tip_amount = ROUND(tip_amount * 83, 2),
                    currency = 'INR';

-- Razorpay order id, captured when an order is created and checked at verify time.
ALTER TABLE payments ADD COLUMN gateway_order_id VARCHAR(255);
