-- RideSwift Phase 3 — payments + Hibernate Envers audit trails.

-- ---------------------------------------------------------------------------
-- payments  (one payment per ride)
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id                UUID NOT NULL
        CONSTRAINT fk_payments_ride REFERENCES rides (id),
    amount                 NUMERIC(10,2) NOT NULL,
    currency               VARCHAR(3)    NOT NULL DEFAULT 'USD',
    gateway                VARCHAR(20)   NOT NULL
        CONSTRAINT chk_payments_gateway CHECK (gateway IN ('PAYPAL', 'STRIPE', 'RAZORPAY')),
    gateway_transaction_id VARCHAR(255),
    status                 VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
    processed_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_payments_ride ON payments (ride_id);

-- ---------------------------------------------------------------------------
-- Envers infrastructure
-- ---------------------------------------------------------------------------
CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE revinfo (
    rev      INTEGER PRIMARY KEY,
    revtstmp BIGINT,
    username VARCHAR(255)
);

-- Audit table for rides (geography/address columns are @NotAudited).
CREATE TABLE rides_aud (
    id               UUID    NOT NULL,
    rev              INTEGER NOT NULL
        CONSTRAINT fk_rides_aud_rev REFERENCES revinfo (rev),
    revtype          SMALLINT,
    passenger_id     UUID,
    driver_id        UUID,
    vehicle_type     VARCHAR(20),
    status           VARCHAR(20),
    requested_at     TIMESTAMPTZ,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    estimated_fare   NUMERIC(10,2),
    actual_fare      NUMERIC(10,2),
    distance_km      NUMERIC(10,3),
    duration_minutes INTEGER,
    PRIMARY KEY (id, rev)
);

-- Audit table for payments.
CREATE TABLE payments_aud (
    id                     UUID    NOT NULL,
    rev                    INTEGER NOT NULL
        CONSTRAINT fk_payments_aud_rev REFERENCES revinfo (rev),
    revtype                SMALLINT,
    ride_id                UUID,
    amount                 NUMERIC(10,2),
    currency               VARCHAR(3),
    gateway                VARCHAR(20),
    gateway_transaction_id VARCHAR(255),
    status                 VARCHAR(20),
    processed_at           TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);
