-- ---------------------------------------------------------------------------
-- Sri Vinayaka Bakery Home :: inventory categories (accordion browsing + filters)
-- Purely additive, so it applies cleanly on top of a database already
-- carrying V1-V3 and live data.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS inventory_categories (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(80)  NOT NULL,
    slug           VARCHAR(100) NOT NULL,
    icon           VARCHAR(8),
    veg            BOOLEAN,
    display_order  INTEGER      NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    archived       BOOLEAN      NOT NULL DEFAULT FALSE,
    archived_at    TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_categories_name UNIQUE (name),
    CONSTRAINT uk_inventory_categories_slug UNIQUE (slug)
);

CREATE INDEX IF NOT EXISTS idx_inventory_categories_archived ON inventory_categories (archived);

-- Seed the families asked for. Admins can add more from the Categories screen later -
-- these five are just the opening set, not a fixed list.
INSERT INTO inventory_categories (name, slug, icon, veg, display_order) VALUES
    ('Veg Pickles',     'veg-pickles',     '🥭', TRUE,  1),
    ('Non-Veg Pickles', 'non-veg-pickles', '🍗', FALSE, 2),
    ('Podi',            'podi',            '🌶',  TRUE,  3),
    ('Snacks',          'snacks',          '🍿', NULL,  4),
    ('Cakes',           'cakes',           '🎂', NULL,  5)
ON CONFLICT (slug) DO NOTHING;

-- Every product needs a home category. Existing rows are all pickles, so they map
-- straight across from their existing veg/non-veg flag.
ALTER TABLE products ADD COLUMN IF NOT EXISTS inventory_category_id BIGINT;

UPDATE products SET inventory_category_id =
    (SELECT id FROM inventory_categories WHERE slug = 'veg-pickles')
    WHERE pickle_type = 'VEG';
UPDATE products SET inventory_category_id =
    (SELECT id FROM inventory_categories WHERE slug = 'non-veg-pickles')
    WHERE pickle_type = 'NON_VEG';

ALTER TABLE products ALTER COLUMN inventory_category_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_inventory_category'
    ) THEN
        ALTER TABLE products ADD CONSTRAINT fk_products_inventory_category
            FOREIGN KEY (inventory_category_id) REFERENCES inventory_categories (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_products_inventory_category ON products (inventory_category_id);
