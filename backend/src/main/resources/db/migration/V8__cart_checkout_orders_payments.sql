-- V8: addresses, coupons, cart, orders, payments (Razorpay + COD), invoices

INSERT INTO permissions (code, module, description) VALUES ('COUPON_MANAGE','ORDER','Manage discount coupons');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN' AND p.code = 'COUPON_MANAGE';

CREATE SEQUENCE order_number_seq START 1001;
CREATE SEQUENCE invoice_number_seq START 1;

CREATE TABLE addresses (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label       VARCHAR(40),
    full_name   VARCHAR(120) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    line1       VARCHAR(200) NOT NULL,
    line2       VARCHAR(200),
    city        VARCHAR(80)  NOT NULL,
    state       VARCHAR(80)  NOT NULL,
    postal_code VARCHAR(10)  NOT NULL,
    country     VARCHAR(2)   NOT NULL DEFAULT 'IN',
    latitude    NUMERIC(9,6) CHECK (latitude BETWEEN -90 AND 90),
    longitude   NUMERIC(9,6) CHECK (longitude BETWEEN -180 AND 180),
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_addresses_user ON addresses (user_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_address_default ON addresses (user_id) WHERE is_default AND deleted_at IS NULL;

CREATE TABLE coupons (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(40) NOT NULL,
    description      VARCHAR(300),
    discount_type    VARCHAR(7)  NOT NULL CHECK (discount_type IN ('PERCENT','FIXED')),
    value            NUMERIC(12,2) NOT NULL CHECK (value > 0),
    min_order_amount NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (min_order_amount >= 0),
    max_discount     NUMERIC(12,2) CHECK (max_discount > 0),
    starts_at        TIMESTAMPTZ,
    ends_at          TIMESTAMPTZ,
    usage_limit      INT CHECK (usage_limit >= 1),
    per_user_limit   INT CHECK (per_user_limit >= 1),
    used_count       INT NOT NULL DEFAULT 0,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_coupons_code ON coupons (lower(code)) WHERE deleted_at IS NULL;

CREATE TABLE carts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE cart_items (
    id            BIGSERIAL PRIMARY KEY,
    cart_id       BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products(id),
    quantity      INT NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    configuration JSONB NOT NULL,             -- choices only; prices are always recomputed from the database
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cart_items_cart ON cart_items (cart_id);

CREATE TABLE orders (
    id                       BIGSERIAL PRIMARY KEY,
    order_number             VARCHAR(30) NOT NULL UNIQUE,
    user_id                  BIGINT NOT NULL REFERENCES users(id),
    status                   VARCHAR(30) NOT NULL CHECK (status IN (
        'ORDER_PLACED','PAYMENT_PENDING','PAYMENT_CONFIRMED','TAILOR_ASSIGNMENT_PENDING','TAILOR_ASSIGNED','FABRIC_SAMPLE_PENDING',
        'FABRIC_SAMPLE_SUBMITTED','CUSTOMER_APPROVAL_PENDING','FABRIC_APPROVED','FABRIC_REJECTED','CUTTING_IN_PROGRESS','STITCHING_IN_PROGRESS',
        'QUALITY_CHECK','READY_FOR_DISPATCH','SHIPPED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED','REFUND_PENDING','REFUNDED')),
    payment_method           VARCHAR(10) NOT NULL CHECK (payment_method IN ('RAZORPAY','COD')),
    payment_status           VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (payment_status IN ('PENDING','PENDING_COLLECTION','PAID','FAILED','REFUNDED')),
    tailor_category_id       BIGINT NOT NULL REFERENCES tailor_categories(id),
    tailor_category_name     VARCHAR(80) NOT NULL,                       -- snapshots: later admin edits never change a placed order
    tailor_price_per_garment NUMERIC(12,2) NOT NULL,
    garment_count            INT NOT NULL,
    subtotal                 NUMERIC(12,2) NOT NULL,
    discount                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    coupon_code              VARCHAR(40),
    tailor_charges           NUMERIC(12,2) NOT NULL,
    tax_rate_percent         NUMERIC(5,2)  NOT NULL,
    tax                      NUMERIC(12,2) NOT NULL,
    delivery_charge          NUMERIC(12,2) NOT NULL,
    total                    NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    currency                 VARCHAR(3) NOT NULL DEFAULT 'INR',
    shipping_address         JSONB NOT NULL,
    customer_name            VARCHAR(120) NOT NULL,
    customer_phone           VARCHAR(20) NOT NULL,
    delivery_instructions    VARCHAR(500),
    tailor_id                BIGINT REFERENCES users(id),                -- set by assignment (Phase 9)
    placed_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_user ON orders (user_id, placed_at DESC);
CREATE INDEX idx_orders_status ON orders (status, placed_at DESC);
CREATE INDEX idx_orders_pending_assignment ON orders (tailor_category_id) WHERE status = 'TAILOR_ASSIGNMENT_PENDING';

CREATE TABLE order_items (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products(id),
    product_name  VARCHAR(160) NOT NULL,
    product_slug  VARCHAR(180) NOT NULL,
    image_url     VARCHAR(500),
    quantity      INT NOT NULL CHECK (quantity >= 1),
    unit_price    NUMERIC(12,2) NOT NULL,
    line_total    NUMERIC(12,2) NOT NULL,
    size_code     VARCHAR(10),
    measurements  JSONB,                      -- snapshot of the customer's profile in cm at order time
    measurement_profile_name VARCHAR(80),
    configuration JSONB NOT NULL,             -- the ids the customer chose
    details       JSONB NOT NULL              -- readable snapshot for the tailor: fabric, colour, selections, price lines
);
CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE order_status_history (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status     VARCHAR(30) NOT NULL,
    note       VARCHAR(500),
    actor_id   BIGINT REFERENCES users(id),   -- NULL = system
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_order_history_order ON order_status_history (order_id, created_at);

CREATE TABLE coupon_redemptions (
    id              BIGSERIAL PRIMARY KEY,
    coupon_id       BIGINT NOT NULL REFERENCES coupons(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    order_id        BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    discount_amount NUMERIC(12,2) NOT NULL,
    released_at     TIMESTAMPTZ,              -- set when the order is cancelled before payment
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_coupon_redemptions ON coupon_redemptions (coupon_id, user_id);

CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders(id),
    user_id             BIGINT NOT NULL REFERENCES users(id),
    method              VARCHAR(10) NOT NULL CHECK (method IN ('RAZORPAY','COD')),
    status              VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','PENDING_COLLECTION','SUCCESS','FAILED','SUPERSEDED')),
    amount              NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    provider_order_id   VARCHAR(60),
    provider_payment_id VARCHAR(60),
    payment_instrument  VARCHAR(30),          -- upi, card, netbanking... as reported by the provider; never card numbers
    failure_reason      VARCHAR(300),
    paid_at             TIMESTAMPTZ,
    collected_by        BIGINT REFERENCES users(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_payments_provider_order ON payments (provider_order_id) WHERE provider_order_id IS NOT NULL;
CREATE UNIQUE INDEX uq_payments_provider_payment ON payments (provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_payments_status ON payments (status, created_at DESC);

CREATE TABLE payment_transactions (            -- append-only audit trail for every payment event
    id         BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    type       VARCHAR(30) NOT NULL,
    amount     NUMERIC(12,2),
    provider_ref VARCHAR(60),
    detail     VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_tx_payment ON payment_transactions (payment_id, created_at);

CREATE TABLE payment_webhook_events (
    id           BIGSERIAL PRIMARY KEY,
    event_id     VARCHAR(80) NOT NULL UNIQUE,   -- Razorpay's event id: a repeat delivery cannot be processed twice
    event_type   VARCHAR(60) NOT NULL,
    payload      JSONB NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invoices (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT NOT NULL UNIQUE REFERENCES orders(id),
    invoice_number VARCHAR(30) NOT NULL UNIQUE,
    issued_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Checkout charges. ILLUSTRATIVE DEFAULTS: confirm the correct GST rate and delivery policy for your business, then change them in Admin > Website settings.
INSERT INTO website_settings (setting_key, setting_value) VALUES
 ('checkout.taxRatePercent', '5'),
 ('checkout.deliveryCharge', '99'),
 ('checkout.freeDeliveryAbove', '0'),
 ('checkout.codEnabled', 'true');
