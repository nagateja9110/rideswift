-- RideSwift Phase 6 — in-ride chat between the passenger and the assigned driver.
CREATE TABLE ride_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ride_id    UUID NOT NULL
        CONSTRAINT fk_ride_messages_ride REFERENCES rides (id) ON DELETE CASCADE,
    sender_id  UUID NOT NULL
        CONSTRAINT fk_ride_messages_sender REFERENCES users (id) ON DELETE CASCADE,
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_ride_messages_ride ON ride_messages (ride_id, created_at);
