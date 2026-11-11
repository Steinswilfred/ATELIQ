-- V4: in-app notifications, tailor application review history

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(60)  NOT NULL,
    title      VARCHAR(160) NOT NULL,
    body       VARCHAR(1000),
    link       VARCHAR(300),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

-- Final category becomes a FK to tailor_categories in Phase 7
ALTER TABLE tailor_applications ADD COLUMN approved_category VARCHAR(80);

CREATE TABLE tailor_application_reviews (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES tailor_applications(id) ON DELETE CASCADE,
    reviewer_id    BIGINT NOT NULL REFERENCES users(id),
    decision       VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVED','REJECTED','INFO_REQUESTED','NOTE')),
    reason         TEXT,
    notes          TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_reviews_app ON tailor_application_reviews (application_id, created_at DESC);
