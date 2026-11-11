-- V11: fabric sample submissions, photos and the approval history

CREATE TABLE fabric_samples (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    revision        INT NOT NULL CHECK (revision >= 1),
    submitted_by    BIGINT NOT NULL REFERENCES users(id),     -- the tailor
    note            VARCHAR(500),
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ,
    decided_by      BIGINT REFERENCES users(id),              -- the customer
    decision_reason VARCHAR(500),
    UNIQUE (order_id, revision)
);
-- Only one submission can be waiting for the customer at a time.
CREATE UNIQUE INDEX uq_fabric_sample_pending ON fabric_samples (order_id) WHERE status = 'PENDING';

CREATE TABLE fabric_sample_photos (
    id         BIGSERIAL PRIMARY KEY,
    sample_id  BIGINT NOT NULL REFERENCES fabric_samples(id) ON DELETE CASCADE,
    url        VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_fabric_photos_sample ON fabric_sample_photos (sample_id, sort_order);

-- Append-only: who did what and when, for every submission and decision.
CREATE TABLE fabric_approvals (
    id         BIGSERIAL PRIMARY KEY,
    sample_id  BIGINT NOT NULL REFERENCES fabric_samples(id) ON DELETE CASCADE,
    action     VARCHAR(10) NOT NULL CHECK (action IN ('SUBMITTED','APPROVED','REJECTED')),
    actor_id   BIGINT NOT NULL REFERENCES users(id),
    actor_role VARCHAR(10) NOT NULL CHECK (actor_role IN ('TAILOR','CUSTOMER')),
    reason     VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fabric_approvals_sample ON fabric_approvals (sample_id, created_at);
