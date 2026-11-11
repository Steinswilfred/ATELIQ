-- V2: system roles, base permission catalogue, default theme. Permissions are data: admins can add more later.

INSERT INTO roles (code, name, description, is_system) VALUES
 ('SUPER_ADMIN','Super Admin','Full platform control',TRUE),
 ('ADMIN','Admin','Base admin role; capabilities come from assigned permissions',TRUE),
 ('TAILOR','Tailor','Approved tailor',TRUE),
 ('CUSTOMER','Customer','Registered customer',TRUE);

INSERT INTO permissions (code, module, description) VALUES
 ('ADMIN_CREATE','ADMIN','Create admin accounts'),
 ('ADMIN_MANAGE','ADMIN','Edit, block, reactivate admins'),
 ('ROLE_MANAGE','RBAC','Manage roles and permissions'),
 ('USER_VIEW','USER','View customers and tailors'),
 ('USER_MANAGE','USER','Edit, block, reactivate users'),
 ('TAILOR_APPLICATION_REVIEW','TAILOR','Approve or reject tailor applications'),
 ('TAILOR_CATEGORY_MANAGE','TAILOR','Manage tailor categories and pricing'),
 ('PRODUCT_VIEW','PRODUCT','View product admin'),
 ('PRODUCT_WRITE','PRODUCT','Create, edit, archive products'),
 ('CATEGORY_MANAGE','PRODUCT','Manage categories'),
 ('CUSTOMIZATION_MANAGE','PRODUCT','Manage customization groups and options'),
 ('ORDER_VIEW','ORDER','View all orders'),
 ('ORDER_MANAGE','ORDER','Update status, reassign tailor'),
 ('DELIVERY_MANAGE','ORDER','Update delivery status'),
 ('PAYMENT_VIEW','PAYMENT','View payments'),
 ('PAYMENT_COD_COLLECT','PAYMENT','Mark COD payments collected'),
 ('REFUND_INITIATE','PAYMENT','Initiate refunds'),
 ('REFUND_APPROVE','PAYMENT','Approve refunds'),
 ('REPORT_VIEW','REPORT','View and export reports'),
 ('LOG_VIEW','AUDIT','View activity, security and audit logs'),
 ('SETTINGS_MANAGE','SETTINGS','Website, theme and system settings');

-- SUPER_ADMIN gets every permission; further admin roles are created by the Super Admin in the UI.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

INSERT INTO website_settings (setting_key, setting_value) VALUES
 ('site.name',   '"Atelier"'),
 ('theme.colors','{"primary":"#E8505B","secondary":"#0A0A0A","background":"#FFFFFF","surface":"#F6F6F7","accent":"#F4A0A6","text":"#2B2B2E"}'),
 ('theme.fonts', '{"heading":"Bricolage Grotesque","body":"Manrope","baseSize":16}');
