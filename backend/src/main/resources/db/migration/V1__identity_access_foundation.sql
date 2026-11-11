-- V1: identity, RBAC, verification, tokens, logs, website settings (Phase 1 foundation)

CREATE TABLE roles (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(60)  NOT NULL UNIQUE,
    name         VARCHAR(120) NOT NULL,
    description  VARCHAR(500),
    is_system    BOOLEAN      NOT NULL DEFAULT FALSE,   -- system roles cannot be deleted
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE permissions (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(80)  NOT NULL UNIQUE,          -- e.g. PRODUCT_WRITE
    module       VARCHAR(60)  NOT NULL,                 -- e.g. PRODUCT
    description  VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE role_permissions (
    role_id        BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id  BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,       -- enable/disable without unassigning
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id                BIGSERIAL PRIMARY KEY,
    email             VARCHAR(255) NOT NULL,
    phone             VARCHAR(20),
    password_hash     VARCHAR(100) NOT NULL,
    full_name         VARCHAR(160) NOT NULL,
    user_type         VARCHAR(20)  NOT NULL CHECK (user_type IN ('CUSTOMER','TAILOR','ADMIN','SUPER_ADMIN')),
    account_status    VARCHAR(40)  NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN
        ('PENDING_EMAIL_VERIFICATION','PENDING_PHONE_VERIFICATION','PENDING_APPROVAL',
         'ACTIVE','APPROVED','REJECTED','BLOCKED','SUSPENDED','DEACTIVATED')),
    status_reason     VARCHAR(500),
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    phone_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by        BIGINT REFERENCES users(id),      -- which Super Admin created an Admin
    last_login_at     TIMESTAMPTZ,
    failed_login_count INT         NOT NULL DEFAULT 0,
    locked_until      TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ,                      -- soft delete
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_users_email_active ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_phone_active ON users (phone) WHERE deleted_at IS NULL AND phone IS NOT NULL;
CREATE INDEX idx_users_type_status ON users (user_type, account_status);

CREATE TABLE user_roles (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_by BIGINT REFERENCES users(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

-- Per-user permission overrides (grant or revoke on top of role permissions)
CREATE TABLE user_permission_overrides (
    user_id       BIGINT  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permission_id BIGINT  NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    granted       BOOLEAN NOT NULL,
    set_by        BIGINT REFERENCES users(id),
    set_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, permission_id)
);

CREATE TABLE user_verifications (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel       VARCHAR(10) NOT NULL CHECK (channel IN ('EMAIL','PHONE','PASSWORD_RESET')),
    token_hash    VARCHAR(128) NOT NULL,                -- hash of token/OTP, never plaintext
    expires_at    TIMESTAMPTZ NOT NULL,
    consumed_at   TIMESTAMPTZ,
    attempts      INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_verifications_lookup ON user_verifications (user_id, channel, consumed_at);

CREATE TABLE refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id     UUID   NOT NULL,                      -- rotation family; reuse of a used token revokes the family
    token_hash    VARCHAR(128) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(300),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE login_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    email       VARCHAR(255),
    success     BOOLEAN NOT NULL,
    failure_reason VARCHAR(120),
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(300),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_logs_user_time ON login_logs (user_id, created_at DESC);

CREATE TABLE activity_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    action      VARCHAR(120) NOT NULL,
    entity_type VARCHAR(80),
    entity_id   VARCHAR(64),
    metadata    JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_activity_logs_user_time ON activity_logs (user_id, created_at DESC);

-- Append-only audit trail for approvals, permission changes, payments, refunds
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT REFERENCES users(id),
    action      VARCHAR(120) NOT NULL,
    entity_type VARCHAR(80)  NOT NULL,
    entity_id   VARCHAR(64)  NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    reason      VARCHAR(500),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_actor_time ON audit_logs (actor_id, created_at DESC);

CREATE TABLE website_settings (
    id          BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value JSONB NOT NULL,
    is_public   BOOLEAN NOT NULL DEFAULT TRUE,          -- public settings are served to the storefront
    updated_by  BIGINT REFERENCES users(id),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
