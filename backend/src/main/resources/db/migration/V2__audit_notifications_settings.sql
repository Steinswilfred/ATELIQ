-- Phase 1: audit trail, activity feed, notifications, website settings

CREATE TABLE audit_logs (
    id             BIGSERIAL PRIMARY KEY,
    actor_user_id  BIGINT REFERENCES users (id) ON DELETE SET NULL,
    action         VARCHAR(100) NOT NULL,        -- e.g. TAILOR_APPROVED, ADMIN_CREATED, REFUND_APPROVED
    entity_type    VARCHAR(80)  NOT NULL,
    entity_id      VARCHAR(64),
    before_state   JSONB,
    after_state    JSONB,
    ip_address     VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_actor ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_action ON audit_logs (action, created_at DESC);

CREATE TABLE activity_logs (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT REFERENCES users (id) ON DELETE SET NULL,
    activity_type  VARCHAR(80) NOT NULL,
    description    VARCHAR(500),
    metadata       JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_activity_user ON activity_logs (user_id, created_at DESC);

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(80)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        VARCHAR(1000),
    data        JSONB,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;

-- Key/value store for admin-editable theme, branding, banners, homepage sections
CREATE TABLE website_settings (
    id             BIGSERIAL PRIMARY KEY,
    setting_key    VARCHAR(120) NOT NULL UNIQUE,
    setting_value  JSONB        NOT NULL,
    is_public      BOOLEAN      NOT NULL DEFAULT FALSE,   -- public settings are served to the storefront
    updated_by     BIGINT REFERENCES users (id),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
