-- V9: tailor assignment history, escalation marker, assignment rules

CREATE TABLE tailor_assignments (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    tailor_user_id BIGINT NOT NULL REFERENCES users(id),
    method         VARCHAR(14) NOT NULL CHECK (method IN ('ACCEPTED','ADMIN_ASSIGNED')),
    status         VARCHAR(8)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','RELEASED')),
    assigned_by    BIGINT REFERENCES users(id),        -- the admin, for ADMIN_ASSIGNED
    assign_reason  VARCHAR(300),
    assigned_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at    TIMESTAMPTZ,
    released_by    BIGINT REFERENCES users(id),
    release_reason VARCHAR(300)
);
-- The database itself refuses a second active assignment for an order, even if application code had a bug.
CREATE UNIQUE INDEX uq_active_assignment ON tailor_assignments (order_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_assignments_order ON tailor_assignments (order_id, assigned_at);
CREATE INDEX idx_assignments_tailor ON tailor_assignments (tailor_user_id, status);

ALTER TABLE orders ADD COLUMN escalated_at TIMESTAMPTZ;
CREATE INDEX idx_orders_tailor_status ON orders (tailor_id, status);

-- Assignment rules (admin-editable, not public). 24 = notify admins when no tailor accepted within 24 hours; 0 = off.
INSERT INTO website_settings (setting_key, setting_value, is_public) VALUES
 ('assignment.requireAvailable', 'true', FALSE),
 ('assignment.respectCapacity',  'true', FALSE),
 ('assignment.escalationHours',  '24',   FALSE);
