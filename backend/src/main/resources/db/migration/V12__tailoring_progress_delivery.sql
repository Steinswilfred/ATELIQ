-- V12: photos on order timeline entries, delivery tracking (admin-managed; courier integration plugs in later)

ALTER TABLE order_status_history ADD COLUMN photos JSONB;      -- e.g. completed-garment photos shown on the timeline

CREATE TABLE delivery_tracking (
    order_id           BIGINT PRIMARY KEY REFERENCES orders(id) ON DELETE CASCADE,
    provider           VARCHAR(20) NOT NULL DEFAULT 'MANUAL',   -- MANUAL = entered by an admin; a courier integration would set its own name
    courier_name       VARCHAR(80) NOT NULL,
    tracking_number    VARCHAR(80),
    tracking_url       VARCHAR(500),
    estimated_delivery DATE,
    shipped_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at       TIMESTAMPTZ,
    created_by         BIGINT REFERENCES users(id)
);

CREATE TABLE delivery_events (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status       VARCHAR(30) NOT NULL,                          -- SHIPPED, OUT_FOR_DELIVERY, DELIVERED or UPDATE
    note         VARCHAR(500),
    location     VARCHAR(120),                                  -- free text typed by an admin; never GPS
    occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by  BIGINT REFERENCES users(id)
);
CREATE INDEX idx_delivery_events_order ON delivery_events (order_id, occurred_at);
