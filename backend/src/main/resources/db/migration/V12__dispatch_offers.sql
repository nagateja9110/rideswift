-- ---------------------------------------------------------------------------
-- Smart dispatch: per-ride driver offers with timeouts.
-- A ride is offered to the nearest driver; on decline/timeout it rolls to the
-- next, and expires if nobody accepts. ride_offers records every offer so the
-- dispatcher never re-offers a ride to a driver who already passed on it.
-- ---------------------------------------------------------------------------

-- When the current outstanding offer lapses (driver didn't respond). NotAudited
-- on the entity, so no rides_aud mirror is needed.
ALTER TABLE rides ADD COLUMN offer_expires_at TIMESTAMPTZ;

CREATE TABLE ride_offers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id      UUID        NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
    driver_id    UUID        NOT NULL REFERENCES drivers(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'OFFERED',  -- OFFERED / ACCEPTED / DECLINED / EXPIRED
    offered_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ
);

CREATE INDEX ix_ride_offers_ride ON ride_offers (ride_id);
CREATE INDEX ix_ride_offers_active ON ride_offers (status, expires_at);
