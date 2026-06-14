-- RideSwift Phase 4 — in-app / push / sms / email notifications.
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL
        CONSTRAINT fk_notifications_user REFERENCES users (id) ON DELETE CASCADE,
    message    TEXT        NOT NULL,
    type       VARCHAR(10) NOT NULL
        CONSTRAINT chk_notifications_type CHECK (type IN ('SMS', 'PUSH', 'EMAIL')),
    is_read    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_notifications_user ON notifications (user_id, is_read);
