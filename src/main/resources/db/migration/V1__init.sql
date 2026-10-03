-- ---------------------------------------------------------------------------
-- Sri Vinayaka Bakery Home :: initial schema
-- All money is stored as whole paise (INR * 100) in BIGINT to avoid rounding.
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    phone         VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE products (
    id              BIGSERIAL PRIMARY KEY,
    sku             VARCHAR(64)  NOT NULL,
    slug            VARCHAR(180) NOT NULL,
    name            VARCHAR(160) NOT NULL,
    description     TEXT,
    pickle_type     VARCHAR(16)  NOT NULL,
    category        VARCHAR(64)  NOT NULL,
    spice_level     VARCHAR(16)  NOT NULL,
    price_paise     BIGINT       NOT NULL,
    mrp_paise       BIGINT,
    weight_grams    INTEGER      NOT NULL,
    shelf_life_days INTEGER      NOT NULL DEFAULT 365,
    ingredients     TEXT,
    image_url       VARCHAR(500),
    stock_qty       INTEGER      NOT NULL DEFAULT 0,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT uk_products_slug UNIQUE (slug),
    CONSTRAINT ck_products_type CHECK (pickle_type IN ('VEG', 'NON_VEG')),
    CONSTRAINT ck_products_price CHECK (price_paise > 0),
    CONSTRAINT ck_products_stock CHECK (stock_qty >= 0)
);

CREATE INDEX idx_products_type ON products (pickle_type);
CREATE INDEX idx_products_active ON products (active);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    product_id BIGINT    NOT NULL,
    quantity   INTEGER   NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_cart_user_product UNIQUE (user_id, product_id),
    CONSTRAINT ck_cart_qty CHECK (quantity > 0)
);

CREATE TABLE orders (
    id             BIGSERIAL PRIMARY KEY,
    order_number   VARCHAR(64) NOT NULL,
    user_id        BIGINT,
    status         VARCHAR(24) NOT NULL,
    subtotal_paise BIGINT      NOT NULL,
    shipping_paise BIGINT      NOT NULL DEFAULT 0,
    total_paise    BIGINT      NOT NULL,
    contact_name   VARCHAR(120) NOT NULL,
    contact_email  VARCHAR(160) NOT NULL,
    contact_phone  VARCHAR(20)  NOT NULL,
    address_line1  VARCHAR(200) NOT NULL,
    address_line2  VARCHAR(200),
    city           VARCHAR(80)  NOT NULL,
    state          VARCHAR(80)  NOT NULL,
    pincode        VARCHAR(10)  NOT NULL,
    notes          VARCHAR(500),
    placed_at      TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_orders_user ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);

CREATE TABLE order_items (
    id               BIGSERIAL PRIMARY KEY,
    order_id         BIGINT       NOT NULL,
    product_id       BIGINT       NOT NULL,
    product_name     VARCHAR(160) NOT NULL,
    sku              VARCHAR(64)  NOT NULL,
    pickle_type      VARCHAR(16)  NOT NULL,
    weight_grams     INTEGER      NOT NULL,
    unit_price_paise BIGINT       NOT NULL,
    quantity         INTEGER      NOT NULL,
    line_total_paise BIGINT       NOT NULL,
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);

CREATE TABLE payments (
    id                BIGSERIAL PRIMARY KEY,
    order_id          BIGINT       NOT NULL,
    provider          VARCHAR(32)  NOT NULL DEFAULT 'PHONEPE',
    merchant_order_id VARCHAR(63)  NOT NULL,
    provider_order_id VARCHAR(120),
    transaction_id    VARCHAR(120),
    amount_paise      BIGINT       NOT NULL,
    status            VARCHAR(24)  NOT NULL,
    payment_mode      VARCHAR(32),
    redirect_url      TEXT,
    error_code        VARCHAR(64),
    error_message     VARCHAR(500),
    raw_response      TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payments_merchant_order UNIQUE (merchant_order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_payments_status ON payments (status);
