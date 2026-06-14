-- RideSwift Phase 2 — core ride flow: fare rules + rides.

-- ---------------------------------------------------------------------------
-- fare_rules
-- One active rule per vehicle type drives the standard fare calculation.
-- ---------------------------------------------------------------------------
CREATE TABLE fare_rules (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_type     VARCHAR(20)   NOT NULL
        CONSTRAINT chk_fare_type CHECK (vehicle_type IN ('ECONOMY', 'PREMIUM', 'XL')),
    base_fare        NUMERIC(10,2) NOT NULL,
    per_km_rate      NUMERIC(10,2) NOT NULL,
    per_minute_rate  NUMERIC(10,2) NOT NULL,
    surge_multiplier NUMERIC(4,2)  NOT NULL DEFAULT 1.00
        CONSTRAINT chk_fare_surge CHECK (surge_multiplier >= 1.00),
    effective_from   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    effective_to     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Fast lookup of the rule in effect for a vehicle type.
CREATE INDEX ix_fare_rules_lookup ON fare_rules (vehicle_type, effective_from);

INSERT INTO fare_rules (vehicle_type, base_fare, per_km_rate, per_minute_rate, surge_multiplier, effective_from)
VALUES
    ('ECONOMY', 2.50, 1.20, 0.30, 1.00, TIMESTAMPTZ '2020-01-01 00:00:00+00'),
    ('PREMIUM', 5.00, 2.00, 0.50, 1.00, TIMESTAMPTZ '2020-01-01 00:00:00+00'),
    ('XL',      4.00, 1.80, 0.45, 1.00, TIMESTAMPTZ '2020-01-01 00:00:00+00');

-- ---------------------------------------------------------------------------
-- rides
-- vehicle_type is captured at request time so a fare can be estimated before
-- a driver (and therefore a concrete vehicle) is assigned.
-- ---------------------------------------------------------------------------
CREATE TABLE rides (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passenger_id     UUID NOT NULL
        CONSTRAINT fk_rides_passenger REFERENCES users (id),
    driver_id        UUID
        CONSTRAINT fk_rides_driver REFERENCES drivers (id),
    vehicle_type     VARCHAR(20)  NOT NULL
        CONSTRAINT chk_rides_vehicle_type CHECK (vehicle_type IN ('ECONOMY', 'PREMIUM', 'XL')),
    pickup_location  geography(Point, 4326) NOT NULL,
    dropoff_location geography(Point, 4326) NOT NULL,
    pickup_address   TEXT,
    dropoff_address  TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED'
        CONSTRAINT chk_rides_status
            CHECK (status IN ('REQUESTED', 'MATCHED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    requested_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    estimated_fare   NUMERIC(10,2),
    actual_fare      NUMERIC(10,2),
    distance_km      NUMERIC(10,3),
    duration_minutes INTEGER,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_rides_passenger ON rides (passenger_id);
CREATE INDEX ix_rides_driver ON rides (driver_id);
CREATE INDEX ix_rides_status ON rides (status);
