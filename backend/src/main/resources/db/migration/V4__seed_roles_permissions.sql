-- Reference data only. Roles/permissions remain editable by the Super Admin afterwards.
-- No user accounts are seeded here: the Super Admin is created at first start from environment variables (Phase 2).

INSERT INTO roles (code, name, description, system_role) VALUES
 ('SUPER_ADMIN', 'Super Admin', 'Full platform control. Only role that can create Admins by default.', TRUE),
 ('ADMIN',       'Admin',       'Base admin role. Effective access is whatever the Super Admin grants.', TRUE),
 ('CUSTOMER',    'Customer',    'Storefront customer.', TRUE),
 ('TAILOR',      'Tailor',      'Approved tailor with dashboard access.', TRUE);

INSERT INTO permissions (code, module, description) VALUES
 ('dashboard:view',        'dashboard',  'View admin dashboard'),
 ('user:read',             'user',       'View customers and tailors'),
 ('user:manage',           'user',       'Edit, block and reactivate customers'),
 ('admin:create',          'admin',      'Create other Admin accounts'),
 ('admin:manage',          'admin',      'Edit, block and revoke Admin accounts'),
 ('role:manage',           'rbac',       'Create and edit roles'),
 ('permission:manage',     'rbac',       'Create permissions and assign them to roles'),
 ('product:read',          'product',    'View products in admin'),
 ('product:manage',        'product',    'Create, edit and archive products'),
 ('category:manage',       'product',    'Manage categories and collections'),
 ('customization:manage',  'product',    'Manage fabrics, colors, sizes, customization options'),
 ('tailor:read',           'tailor',     'View tailors and applications'),
 ('tailor:approve',        'tailor',     'Approve, reject or request info on tailor applications'),
 ('tailor:manage',         'tailor',     'Edit tailors, categories, pricing and availability'),
 ('order:read',            'order',      'View all orders'),
 ('order:manage',          'order',      'Update order status and reassign tailors'),
 ('delivery:manage',       'order',      'Manage delivery status updates'),
 ('payment:read',          'payment',    'View payments and transactions'),
 ('payment:collect_cod',   'payment',    'Mark COD payments as collected'),
 ('refund:approve',        'payment',    'Initiate or approve refunds'),
 ('finance:manage',        'payment',    'Commission, settlement rules and refund policy'),
 ('report:view',           'report',     'View and export reports'),
 ('log:view',              'audit',      'View activity, audit, security and login logs'),
 ('settings:manage',       'settings',   'Website, theme and system settings'),
 ('notification:manage',   'settings',   'Notification settings and templates'),
 ('coupon:manage',         'marketing',  'Manage coupons and trending collections'),
 ('customer:shop',         'customer',   'Customer storefront actions'),
 ('tailor:work',           'tailor-portal', 'Tailor portal actions on own assigned orders');

-- Super Admin gets everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

-- Customer and Tailor baseline
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'customer:shop' WHERE r.code = 'CUSTOMER';
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'tailor:work' WHERE r.code = 'TAILOR';
-- ADMIN role intentionally starts with no permissions; the Super Admin assigns them (spec section 22).

-- Sample tailor categories for DEVELOPMENT. Prices are placeholders; the admin edits or replaces them in the UI.
INSERT INTO tailor_categories (code, name, description, stitching_price, sort_order) VALUES
 ('BEGINNER',     'Beginner',     'Lowest stitching price',  0.00, 1),
 ('INTERMEDIATE', 'Intermediate', 'Standard stitching price', 0.00, 2),
 ('PROFESSIONAL', 'Professional', 'Higher stitching price',   0.00, 3),
 ('EXPERT',       'Expert',       'Premium stitching price',  0.00, 4),
 ('MASTER',       'Master Tailor','Highest stitching price',  0.00, 5);

-- Default public theme tokens (light red / black / white); admin overrides at runtime
INSERT INTO website_settings (setting_key, setting_value, is_public) VALUES
 ('theme.tokens', '{"primary":"#F0616D","primarySoft":"#FDE6E8","ink":"#141114","background":"#FFFFFF","surface":"#F6F4F4","text":"#2B2628"}', TRUE),
 ('site.identity', '{"name":"Stitchline","logoUrl":null,"faviconUrl":null}', TRUE);
