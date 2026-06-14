-- RideSwift Phase 1 — foundation schema
-- Enables PostGIS for geospatial driver locations (used heavily in Phase 2).
CREATE EXTENSION IF NOT EXISTS postgis;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(120)  NOT NULL,
    email           VARCHAR(255)  NOT NULL,
    phone           VARCHAR(32)   NOT NULL,
    hashed_password VARCHAR(255)  NOT NULL,
    role            VARCHAR(20)   NOT NULL
        CONSTRAINT chk_users_role CHECK (role IN ('PASSENGER', 'DRIVER', 'ADMIN')),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE UNIQUE INDEX ux_users_phone ON users (phone);

-- ---------------------------------------------------------------------------
-- drivers
-- ---------------------------------------------------------------------------
CREATE TABLE drivers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE
        CONSTRAINT fk_drivers_user REFERENCES users (id) ON DELETE CASCADE,
    license_number      VARCHAR(64)  NOT NULL UNIQUE,
    rating              NUMERIC(3,2) NOT NULL DEFAULT 5.00
        CONSTRAINT chk_drivers_rating CHECK (rating >= 0 AND rating <= 5),
    verification_status VARCHAR(24)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_drivers_verification
            CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    is_available        BOOLEAN      NOT NULL DEFAULT false,
    current_location    geography(Point, 4326),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- GIST index backs the Phase 2 nearest-driver search (ST_Distance / ST_DWithin).
CREATE INDEX ix_drivers_location ON drivers USING GIST (current_location);
CREATE INDEX ix_drivers_available ON drivers (is_available)
    WHERE is_available = true;

-- ---------------------------------------------------------------------------
-- vehicles  (a driver may own one or more vehicles)
-- ---------------------------------------------------------------------------
CREATE TABLE vehicles (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id     UUID NOT NULL
        CONSTRAINT fk_vehicles_driver REFERENCES drivers (id) ON DELETE CASCADE,
    make          VARCHAR(60)  NOT NULL,
    model         VARCHAR(60)  NOT NULL,
    year          INTEGER      NOT NULL
        CONSTRAINT chk_vehicles_year CHECK (year BETWEEN 1950 AND 2100),
    license_plate VARCHAR(20)  NOT NULL UNIQUE,
    vehicle_type  VARCHAR(20)  NOT NULL
        CONSTRAINT chk_vehicles_type CHECK (vehicle_type IN ('ECONOMY', 'PREMIUM', 'XL')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_vehicles_driver ON vehicles (driver_id);

-- ---------------------------------------------------------------------------
-- refresh_tokens
-- Phase 1 stores refresh tokens in Postgres so login works with no external
-- dependency. The spec migrates this store to Redis in a later phase.
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL
        CONSTRAINT fk_refresh_user REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_user ON refresh_tokens (user_id);
