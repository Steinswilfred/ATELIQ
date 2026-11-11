-- V7: tailor categories (admin-priced skill levels), tailor profiles, availability

CREATE TABLE tailor_categories (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(80)  NOT NULL,
    description     VARCHAR(500),
    level           INT NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 100),   -- skill order, 1 = lowest
    stitching_price NUMERIC(12,2) NOT NULL CHECK (stitching_price >= 0),      -- charged per garment, set by the admin
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_tailor_categories_name ON tailor_categories (lower(name)) WHERE deleted_at IS NULL;

-- Example categories from the brief. The prices are ILLUSTRATIVE STARTING VALUES: change them under Admin > Tailor categories.
INSERT INTO tailor_categories (name, description, level, stitching_price) VALUES
 ('Beginner',     'Learning tailors for simple garments at the lowest stitching price.', 1,  300.00),
 ('Intermediate', 'Reliable everyday stitching at the standard price.',                  2,  500.00),
 ('Professional', 'Experienced tailors for a higher standard of finish.',                3,  800.00),
 ('Expert',       'Premium craftsmanship for complex and formal garments.',              4, 1200.00),
 ('Master Tailor','Our most senior tailors, for the finest bespoke work.',               5, 2000.00);

CREATE TABLE tailor_profiles (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL UNIQUE REFERENCES users(id),
    category_id      BIGINT NOT NULL REFERENCES tailor_categories(id),
    bio              VARCHAR(2000),
    skills           TEXT,
    experience_years INT NOT NULL DEFAULT 0 CHECK (experience_years >= 0),
    address          VARCHAR(500),
    photo_url        VARCHAR(500),
    rating_avg       NUMERIC(3,2),                       -- filled from customer reviews (later phase)
    rating_count     INT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tailor_profiles_category ON tailor_profiles (category_id);

CREATE TABLE tailor_availability (
    tailor_id          BIGINT PRIMARY KEY REFERENCES tailor_profiles(id) ON DELETE CASCADE,
    status             VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','BUSY','ON_LEAVE')),
    max_active_orders  INT NOT NULL DEFAULT 5 CHECK (max_active_orders BETWEEN 1 AND 50),   -- set by the admin
    note               VARCHAR(300),
    available_again_on DATE,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE tailor_applications ADD COLUMN approved_category_id BIGINT REFERENCES tailor_categories(id);

-- Tailors approved before this migration get a profile in the matching (or lowest) category.
INSERT INTO tailor_profiles (user_id, category_id, experience_years, skills, address)
SELECT DISTINCT ON (a.user_id) a.user_id,
       COALESCE((SELECT c.id FROM tailor_categories c WHERE lower(c.name) = lower(a.approved_category) AND c.deleted_at IS NULL LIMIT 1),
                (SELECT c.id FROM tailor_categories c WHERE c.deleted_at IS NULL ORDER BY c.level LIMIT 1)),
       a.experience_years, a.skills, a.address
FROM tailor_applications a WHERE a.status = 'APPROVED' ORDER BY a.user_id, a.id DESC;
INSERT INTO tailor_availability (tailor_id) SELECT id FROM tailor_profiles;
