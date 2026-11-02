-- Phase 1: identity, RBAC, verification, sessions, login history

CREATE TABLE roles (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    description  VARCHAR(500),
    system_role  BOOLEAN      NOT NULL DEFAULT FALSE,   -- system roles cannot be deleted
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_roles_code ON roles (code) WHERE deleted_at IS NULL;

CREATE TABLE permissions (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(100) NOT NULL UNIQUE,          -- e.g. product:manage
    module       VARCHAR(64)  NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_permissions_module ON permissions (module);

CREATE TABLE role_permissions (
    role_id        BIGINT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id  BIGINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id                     BIGSERIAL PRIMARY KEY,
    email                  VARCHAR(255) NOT NULL,
    phone                  VARCHAR(20),
    password_hash          VARCHAR(100) NOT NULL,
    full_name              VARCHAR(160) NOT NULL,
    user_type              VARCHAR(20)  NOT NULL,
    account_status         VARCHAR(30)  NOT NULL,
    email_verified_at      TIMESTAMPTZ,
    phone_verified_at      TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,
    failed_login_attempts  INT          NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    created_by             BIGINT REFERENCES users (id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_type CHECK (user_type IN ('CUSTOMER','TAILOR','ADMIN','SUPER_ADMIN')),
    CONSTRAINT ck_users_status CHECK (account_status IN (
        'PENDING_EMAIL_VERIFICATION','PENDING_PHONE_VERIFICATION','PENDING_APPROVAL',
        'ACTIVE','REJECTED','BLOCKED','SUSPENDED','DEACTIVATED'))
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_users_phone ON users (phone) WHERE deleted_at IS NULL AND phone IS NOT NULL;
CREATE INDEX idx_users_type_status ON users (user_type, account_status);

CREATE TABLE user_roles (
    user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id      BIGINT NOT NULL REFERENCES roles (id),
    assigned_by  BIGINT REFERENCES users (id),
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_role ON user_roles (role_id);

-- Per-user grant/deny on top of role permissions ("enable or disable individual permissions")
CREATE TABLE user_permission_overrides (
    user_id        BIGINT  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission_id  BIGINT  NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    granted        BOOLEAN NOT NULL,
    set_by         BIGINT REFERENCES users (id),
    set_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, permission_id)
);

-- Email links and phone OTPs. Only hashes are stored.
CREATE TABLE user_verifications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel      VARCHAR(10)  NOT NULL,
    purpose      VARCHAR(30)  NOT NULL,
    secret_hash  VARCHAR(128) NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    consumed_at  TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_verif_channel CHECK (channel IN ('EMAIL','PHONE')),
    CONSTRAINT ck_verif_purpose CHECK (purpose IN ('SIGNUP','PASSWORD_RESET','PHONE_CHANGE'))
);
CREATE INDEX idx_verifications_user ON user_verifications (user_id, channel, purpose);

-- Refresh-token rotation with reuse detection (family_id groups a rotation chain)
CREATE TABLE refresh_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(128) NOT NULL UNIQUE,
    family_id    UUID         NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  BIGINT REFERENCES refresh_tokens (id),
    ip_address   VARCHAR(64),
    user_agent   VARCHAR(400),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_family ON refresh_tokens (family_id);

CREATE TABLE login_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT REFERENCES users (id) ON DELETE SET NULL,
    identifier      VARCHAR(255) NOT NULL,
    success         BOOLEAN      NOT NULL,
    failure_reason  VARCHAR(80),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(400),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_logs_user ON login_logs (user_id, created_at DESC);
CREATE INDEX idx_login_logs_created ON login_logs (created_at DESC);
