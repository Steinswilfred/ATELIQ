-- V6: trend collections, measurement fields/profiles, saved designs

CREATE TABLE trend_collections (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(120) NOT NULL,
    slug         VARCHAR(140) NOT NULL,
    kind         VARCHAR(20)  NOT NULL DEFAULT 'SEASONAL' CHECK (kind IN
        ('SEASONAL','CASUAL','FORMAL','TRADITIONAL','WEDDING','FESTIVAL','MEN','WOMEN','KIDS','OTHER')),
    description  VARCHAR(1000),
    image_url    VARCHAR(500),
    -- Admin-curated today. A future external trend provider fills source/external_ref instead of an admin.
    source       VARCHAR(40)  NOT NULL DEFAULT 'ADMIN',
    external_ref VARCHAR(120),
    sort_order   INT NOT NULL DEFAULT 0,
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_trend_collections_slug ON trend_collections (slug) WHERE deleted_at IS NULL;

CREATE TABLE trend_collection_products (
    collection_id BIGINT NOT NULL REFERENCES trend_collections(id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sort_order    INT NOT NULL DEFAULT 0,
    PRIMARY KEY (collection_id, product_id)
);

CREATE TABLE measurement_fields (
    code        VARCHAR(40) PRIMARY KEY,
    label       VARCHAR(80)  NOT NULL,
    instruction VARCHAR(500) NOT NULL,
    min_cm      NUMERIC(6,1) NOT NULL,
    max_cm      NUMERIC(6,1) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO measurement_fields (code, label, instruction, min_cm, max_cm, sort_order) VALUES
 ('neck','Neck','Wrap the tape around the base of the neck, snug but with one finger of room.',20,60,1),
 ('shoulder','Shoulder width','Measure across the back from the edge of one shoulder bone to the other.',25,70,2),
 ('chest','Chest','Wrap the tape around the fullest part of the chest, under the arms, keeping it level.',50,200,3),
 ('waist','Waist','Measure around the natural waist, the narrowest part of the torso, above the navel.',40,200,4),
 ('hip','Hip','Measure around the fullest part of the hips and seat, feet together.',50,200,5),
 ('sleeve_length','Sleeve length','With the arm slightly bent, measure from the shoulder edge to the wrist bone.',20,90,6),
 ('bicep','Upper arm','Measure around the fullest part of the upper arm, relaxed.',15,70,7),
 ('wrist','Wrist','Measure around the wrist bone.',10,30,8),
 ('shirt_length','Shirt length','From the highest point of the shoulder next to the neck down to where you want the hem.',30,130,9),
 ('inseam','Inseam','From the crotch seam down the inside of the leg to the ankle bone.',30,120,10),
 ('outseam','Trouser length','From the waist down the outside of the leg to the ankle bone.',40,140,11),
 ('thigh','Thigh','Measure around the fullest part of the upper thigh.',25,100,12),
 ('knee','Knee','Measure around the knee, standing.',20,60,13),
 ('ankle','Ankle / bottom opening','Measure around the ankle, or the width you want the trouser bottom to be.',15,50,14);

CREATE TABLE measurement_profiles (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(80) NOT NULL,
    unit         VARCHAR(2)  NOT NULL DEFAULT 'CM' CHECK (unit IN ('CM','IN')),   -- display preference only
    measurements JSONB NOT NULL,                                                  -- always stored in centimetres
    is_default   BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_measurement_profiles_user ON measurement_profiles (user_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_measurement_default ON measurement_profiles (user_id) WHERE is_default AND deleted_at IS NULL;

CREATE TABLE saved_designs (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products(id),
    name          VARCHAR(100) NOT NULL,
    configuration JSONB NOT NULL,       -- {sizeCode, measurementProfileId, fabricId, colorId, options: {groupId: optionId}}
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_saved_designs_user ON saved_designs (user_id, updated_at DESC) WHERE deleted_at IS NULL;
