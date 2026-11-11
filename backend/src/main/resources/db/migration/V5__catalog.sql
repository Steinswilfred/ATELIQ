-- V5: catalog (categories, products, fabrics, colours, sizes, dynamic customization)

INSERT INTO permissions (code, module, description) VALUES ('FABRIC_MANAGE','PRODUCT','Manage fabrics, colours and sizes');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN' AND p.code = 'FABRIC_MANAGE';

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT REFERENCES categories(id),       -- NULL = top-level section (Men / Women / Kids); max two levels
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(140) NOT NULL,
    description VARCHAR(1000),
    image_url   VARCHAR(500),
    sort_order  INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_categories_slug ON categories (slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_categories_parent ON categories (parent_id);
INSERT INTO categories (name, slug, sort_order) VALUES ('Men','men',1), ('Women','women',2), ('Kids','kids',3);

CREATE TABLE colors (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    hex_code   VARCHAR(7)  NOT NULL CHECK (hex_code ~ '^#[0-9A-Fa-f]{6}$'),
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sizes (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(10) NOT NULL UNIQUE,
    name       VARCHAR(60) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO sizes (code, name, sort_order) VALUES
 ('XXS','Extra extra small',1),('XS','Extra small',2),('S','Small',3),('M','Medium',4),
 ('L','Large',5),('XL','Extra large',6),('XXL','2X large',7),('XXXL','3X large',8);

CREATE TABLE fabrics (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    material    VARCHAR(120),
    description VARCHAR(1000),
    price_delta NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price_delta >= 0),   -- added to the product base price
    image_url   VARCHAR(500),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fabric_colors (
    fabric_id BIGINT NOT NULL REFERENCES fabrics(id) ON DELETE CASCADE,
    color_id  BIGINT NOT NULL REFERENCES colors(id),                 -- RESTRICT: deactivate a colour instead of deleting it
    PRIMARY KEY (fabric_id, color_id)
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    name        VARCHAR(160) NOT NULL,
    slug        VARCHAR(180) NOT NULL,
    description TEXT,
    base_price  NUMERIC(12,2) NOT NULL CHECK (base_price >= 0),      -- INR; stitching/tailor charge is added at checkout
    status      VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    is_trending BOOLEAN NOT NULL DEFAULT FALSE,
    model_url   VARCHAR(500),                                         -- GLB for the 3D preview (Phase 6); NULL = 2D fallback
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_products_slug ON products (slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_category_status ON products (category_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_flags ON products (is_featured, is_trending) WHERE deleted_at IS NULL;

CREATE TABLE product_images (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url        VARCHAR(500) NOT NULL,
    alt        VARCHAR(200),
    sort_order INT NOT NULL DEFAULT 0,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_product_primary_image ON product_images (product_id) WHERE is_primary;

CREATE TABLE product_sizes (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    size_id    BIGINT NOT NULL REFERENCES sizes(id),
    PRIMARY KEY (product_id, size_id)
);

CREATE TABLE product_fabrics (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    fabric_id  BIGINT NOT NULL REFERENCES fabrics(id),
    PRIMARY KEY (product_id, fabric_id)
);

-- Dynamic customization: groups (e.g. "Collar type") hold options (e.g. "Spread collar").
CREATE TABLE customization_groups (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(60)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    sort_order  INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_cust_groups_code ON customization_groups (code) WHERE deleted_at IS NULL;

CREATE TABLE customization_options (
    id             BIGSERIAL PRIMARY KEY,
    group_id       BIGINT NOT NULL REFERENCES customization_groups(id),
    code           VARCHAR(60)  NOT NULL,
    name           VARCHAR(120) NOT NULL,
    description    VARCHAR(500),
    image_url      VARCHAR(500),
    price_delta    NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price_delta >= 0),
    -- Tells the 3D/2D renderer what to change, e.g. {"type":"mesh_toggle","target":"collar_spread"} (Phase 6)
    render_binding JSONB,
    sort_order     INT NOT NULL DEFAULT 0,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_cust_options_code ON customization_options (group_id, code) WHERE deleted_at IS NULL;

CREATE TABLE product_customization_groups (
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    group_id    BIGINT NOT NULL REFERENCES customization_groups(id),
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order  INT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, group_id)
);

CREATE TABLE product_customization_options (
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    option_id  BIGINT NOT NULL REFERENCES customization_options(id),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, option_id)
);
