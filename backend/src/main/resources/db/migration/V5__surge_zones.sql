-- RideSwift Phase 6 — geospatial surge zones.
CREATE TABLE surge_zones (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(100) NOT NULL,
    area           geography(Polygon, 4326) NOT NULL,
    min_multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.00
        CONSTRAINT chk_surge_min CHECK (min_multiplier >= 1.00),
    max_multiplier NUMERIC(4,2) NOT NULL DEFAULT 3.00
        CONSTRAINT chk_surge_max CHECK (max_multiplier >= min_multiplier),
    active         BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Spatial index for point-in-zone lookups.
CREATE INDEX ix_surge_zones_area ON surge_zones USING GIST (area);

-- Seed a downtown-SF demo zone (used by the fare/surge endpoint).
INSERT INTO surge_zones (name, area, max_multiplier)
VALUES (
    'Downtown SF',
    ST_GeogFromText('SRID=4326;POLYGON((-122.45 37.75, -122.39 37.75, -122.39 37.80, -122.45 37.80, -122.45 37.75))'),
    3.00
);
