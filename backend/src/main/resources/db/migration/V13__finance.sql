-- V13: tailor earnings and payouts, refunds, financial ledger, finance settings

INSERT INTO permissions (code, module, description) VALUES ('EARNINGS_MANAGE','PAYMENT','Record tailor payouts and view earnings');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN' AND p.code = 'EARNINGS_MANAGE';

-- ILLUSTRATIVE DEFAULTS: decide the real commission, hold period and cancellation fee, then change them in Admin > Website settings.
INSERT INTO website_settings (setting_key, setting_value, is_public) VALUES
 ('finance.commissionPercent',               '15',   FALSE),
 ('finance.settlementHoldDays',              '7',    FALSE),
 ('finance.lateCancellationFeePercent',      '10',   FALSE),
 ('finance.autoApproveCustomerRefunds',      'true', FALSE);

CREATE TABLE tailor_payouts (
    id             BIGSERIAL PRIMARY KEY,
    tailor_user_id BIGINT NOT NULL REFERENCES users(id),
    amount         NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    reference      VARCHAR(100) NOT NULL,         -- bank transfer / UTR reference typed by the admin
    note           VARCHAR(300),
    paid_by        BIGINT NOT NULL REFERENCES users(id),
    paid_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payouts_tailor ON tailor_payouts (tailor_user_id, paid_at DESC);

CREATE TABLE tailor_earnings (
    id               BIGSERIAL PRIMARY KEY,
    order_id         BIGINT NOT NULL UNIQUE REFERENCES orders(id),     -- one earning per order, created when it is delivered
    tailor_user_id   BIGINT NOT NULL REFERENCES users(id),
    gross_amount     NUMERIC(12,2) NOT NULL,                           -- the order's stitching charge
    commission_rate  NUMERIC(5,2)  NOT NULL,                           -- snapshot, so later rate changes never alter it
    commission_amount NUMERIC(12,2) NOT NULL,
    net_amount       NUMERIC(12,2) NOT NULL,
    status           VARCHAR(10) NOT NULL DEFAULT 'ACCRUED' CHECK (status IN ('ACCRUED','PAID','REVERSED')),
    accrued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    payable_after    TIMESTAMPTZ NOT NULL,                             -- end of the settlement hold
    payout_id        BIGINT REFERENCES tailor_payouts(id),
    reversed_at      TIMESTAMPTZ,
    reversal_reason  VARCHAR(300),
    needs_recovery   BOOLEAN NOT NULL DEFAULT FALSE                    -- refunded after the tailor had already been paid
);
CREATE INDEX idx_earnings_tailor ON tailor_earnings (tailor_user_id, status);

CREATE TABLE refunds (
    id                    BIGSERIAL PRIMARY KEY,
    order_id              BIGINT NOT NULL REFERENCES orders(id),
    payment_id            BIGINT REFERENCES payments(id),
    kind                  VARCHAR(12) NOT NULL CHECK (kind IN ('CANCELLATION','ADMIN')),
    amount                NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    fee_retained          NUMERIC(12,2) NOT NULL DEFAULT 0,            -- cancellation fee kept from the amount paid
    status                VARCHAR(12) NOT NULL CHECK (status IN ('REQUESTED','APPROVED','PROCESSING','PROCESSED','FAILED','REJECTED')),
    method                VARCHAR(10) NOT NULL DEFAULT 'RAZORPAY' CHECK (method IN ('RAZORPAY','MANUAL')),
    reason                VARCHAR(500),
    requested_by          BIGINT NOT NULL REFERENCES users(id),
    approved_by           BIGINT REFERENCES users(id),
    reverse_tailor_earning BOOLEAN NOT NULL DEFAULT FALSE,
    provider_refund_id    VARCHAR(60),
    manual_reference      VARCHAR(100),
    failure_reason        VARCHAR(300),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_refund_provider ON refunds (provider_refund_id) WHERE provider_refund_id IS NOT NULL;
-- one open refund per order at a time (a failed one stays open until retried or rejected)
CREATE UNIQUE INDEX uq_refund_open ON refunds (order_id) WHERE status IN ('REQUESTED','APPROVED','PROCESSING','FAILED');
CREATE INDEX idx_refunds_status ON refunds (status, created_at DESC);

-- Append-only record of money effects, one account per kind of money, so customer payments, tailor payables and platform revenue stay separate.
CREATE TABLE ledger_entries (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT REFERENCES orders(id),
    account    VARCHAR(30) NOT NULL CHECK (account IN ('CUSTOMER_PAYMENTS','ITEM_REVENUE','DELIVERY_REVENUE','PLATFORM_COMMISSION','TAX_COLLECTED','TAILOR_PAYABLE','REVENUE_REFUNDED')),
    amount     NUMERIC(12,2) NOT NULL,          -- signed
    ref_type   VARCHAR(20) NOT NULL,
    ref_id     BIGINT,
    note       VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_account_time ON ledger_entries (account, created_at);
CREATE INDEX idx_ledger_order ON ledger_entries (order_id);
