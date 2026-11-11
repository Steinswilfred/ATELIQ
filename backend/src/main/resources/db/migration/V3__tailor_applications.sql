-- V3: tailor registration applications (documents/portfolio uploads arrive with storage in Phase 3)
CREATE TABLE tailor_applications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    address             VARCHAR(500) NOT NULL,
    experience_years    INT NOT NULL CHECK (experience_years >= 0),
    previous_experience TEXT,
    preferred_category  VARCHAR(80),
    skills              TEXT,
    status              VARCHAR(40) NOT NULL DEFAULT 'PENDING_EMAIL_VERIFICATION' CHECK (status IN
        ('PENDING_EMAIL_VERIFICATION','PENDING_PHONE_VERIFICATION','PENDING_APPROVAL','APPROVED','REJECTED','BLOCKED','SUSPENDED')),
    reviewed_by         BIGINT REFERENCES users(id),
    reviewed_at         TIMESTAMPTZ,
    rejection_reason    VARCHAR(500),
    internal_notes      TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tailor_applications_user ON tailor_applications (user_id);
CREATE INDEX idx_tailor_applications_status ON tailor_applications (status, created_at DESC);
