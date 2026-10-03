-- ---------------------------------------------------------------------------
-- Sri Vinayaka Bakery Home :: uploaded product photos, archive flags, super admin role
-- Purely additive, so it applies cleanly on top of a database already
-- carrying V1 and live data.
-- ---------------------------------------------------------------------------

-- Uploaded photos. Bytes live on disk under app.storage.upload-dir;
-- this table only holds the reference.
CREATE TABLE IF NOT EXISTS product_images (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT       NOT NULL,
    filename      VARCHAR(160) NOT NULL,
    original_name VARCHAR(255),
    content_type  VARCHAR(100),
    size_bytes    BIGINT,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    is_primary    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_images_filename UNIQUE (filename),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_product_images_product ON product_images (product_id);

-- Archive: a super admin removes something from every listing without
-- destroying it. Permanent deletion is a second, separate action.
ALTER TABLE products ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_products_archived ON products (archived);

ALTER TABLE orders ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_orders_archived ON orders (archived);

-- V1 stored role as VARCHAR(20); SUPER_ADMIN fits, so only the comment changes.
COMMENT ON COLUMN users.role IS 'USER | ADMIN | SUPER_ADMIN';

-- Useful for the super admin customer search.
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);
