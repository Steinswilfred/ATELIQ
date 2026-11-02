-- Phase 1: tailor categories (admin-configurable), profiles, applications and review history

CREATE TABLE tailor_categories (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(64)    NOT NULL,
    name             VARCHAR(120)   NOT NULL,
    description      VARCHAR(500),
    stitching_price  NUMERIC(12,2)  NOT NULL CHECK (stitching_price >= 0),
    sort_order       INT            NOT NULL DEFAULT 0,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_tailor_categories_code ON tailor_categories (code) WHERE deleted_at IS NULL;

CREATE TABLE tailor_profiles (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    tailor_category_id    BIGINT REFERENCES tailor_categories (id),
    experience_years      INT         NOT NULL DEFAULT 0 CHECK (experience_years >= 0),
    skills                TEXT[]      NOT NULL DEFAULT '{}',
    bio                   VARCHAR(2000),
    availability_status   VARCHAR(20) NOT NULL DEFAULT 'UNAVAILABLE',
    rating_average        NUMERIC(3,2) NOT NULL DEFAULT 0,
    rating_count          INT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_tailor_availability CHECK (availability_status IN ('AVAILABLE','BUSY','UNAVAILABLE'))
);
CREATE INDEX idx_tailor_profiles_category ON tailor_profiles (tailor_category_id, availability_status);

CREATE TABLE tailor_applications (
    id                    BIGSERIAL PRIMARY KEY,
    applicant_user_id     BIGINT      NOT NULL REFERENCES users (id),
    requested_category_id BIGINT REFERENCES tailor_categories (id),
    address_text          VARCHAR(500),
    experience_years      INT         NOT NULL DEFAULT 0 CHECK (experience_years >= 0),
    previous_experience   VARCHAR(2000),
    skills                TEXT[]      NOT NULL DEFAULT '{}',
    status                VARCHAR(30) NOT NULL,
    rejection_reason      VARCHAR(1000),
    info_request_note     VARCHAR(1000),
    submitted_at          TIMESTAMPTZ,
    reviewed_by           BIGINT REFERENCES users (id),
    reviewed_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_tailor_app_status CHECK (status IN (
        'PENDING_EMAIL_VERIFICATION','PENDING_PHONE_VERIFICATION','PENDING_APPROVAL',
        'APPROVED','REJECTED','BLOCKED','SUSPENDED'))
);
-- one open application per applicant at a time
CREATE UNIQUE INDEX uq_tailor_app_open ON tailor_applications (applicant_user_id)
    WHERE status IN ('PENDING_EMAIL_VERIFICATION','PENDING_PHONE_VERIFICATION','PENDING_APPROVAL');
CREATE INDEX idx_tailor_app_status ON tailor_applications (status, submitted_at);

CREATE TABLE tailor_application_documents (
    id               BIGSERIAL PRIMARY KEY,
    application_id   BIGINT       NOT NULL REFERENCES tailor_applications (id) ON DELETE CASCADE,
    doc_type         VARCHAR(20)  NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,       -- object-storage key, never a public URL
    original_name    VARCHAR(255) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL CHECK (size_bytes > 0),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_tailor_doc_type CHECK (doc_type IN ('PORTFOLIO','ID_DOCUMENT','OTHER'))
);
CREATE INDEX idx_tailor_docs_app ON tailor_application_documents (application_id);

-- Append-only approval history: reviewer, decision, reason, time
CREATE TABLE tailor_application_reviews (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT      NOT NULL REFERENCES tailor_applications (id) ON DELETE CASCADE,
    reviewer_id     BIGINT      NOT NULL REFERENCES users (id),
    decision        VARCHAR(20) NOT NULL,
    reason          VARCHAR(1000),
    internal_note   VARCHAR(2000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_tailor_review_decision CHECK (decision IN ('APPROVED','REJECTED','INFO_REQUESTED','NOTE'))
);
CREATE INDEX idx_tailor_reviews_app ON tailor_application_reviews (application_id, created_at);
