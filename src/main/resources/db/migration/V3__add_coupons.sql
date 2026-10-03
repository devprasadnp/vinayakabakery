-- ---------------------------------------------------------------------------
-- Sri Vinayaka Bakery Home :: coupon codes
-- Purely additive, so it applies cleanly on top of a database already
-- carrying V1/V2 and live data.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS coupons (
    id                 BIGSERIAL PRIMARY KEY,
    code               VARCHAR(32)  NOT NULL,
    description        VARCHAR(255),
    discount_type      VARCHAR(16)  NOT NULL,
    percent_off        INTEGER,
    flat_off_paise     BIGINT,
    max_discount_paise BIGINT,
    min_order_paise    BIGINT       NOT NULL DEFAULT 0,
    total_usage_limit  INTEGER,
    per_customer_limit INTEGER,
    used_count         INTEGER      NOT NULL DEFAULT 0,
    start_at           TIMESTAMP,
    end_at             TIMESTAMP,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    archived           BOOLEAN      NOT NULL DEFAULT FALSE,
    archived_at        TIMESTAMP,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupons_type CHECK (discount_type IN ('PERCENT', 'FLAT')),
    CONSTRAINT ck_coupons_percent CHECK (percent_off IS NULL OR (percent_off > 0 AND percent_off <= 100)),
    CONSTRAINT ck_coupons_flat CHECK (flat_off_paise IS NULL OR flat_off_paise > 0)
);

CREATE INDEX IF NOT EXISTS idx_coupons_archived ON coupons (archived);
CREATE INDEX IF NOT EXISTS idx_coupons_active ON coupons (active);

-- One row per successful redemption. This, not the coupon's own counter, is what
-- CouponService checks to enforce a per-customer usage limit.
CREATE TABLE IF NOT EXISTS coupon_redemptions (
    id             BIGSERIAL PRIMARY KEY,
    coupon_id      BIGINT    NOT NULL,
    user_id        BIGINT    NOT NULL,
    order_id       BIGINT    NOT NULL,
    discount_paise BIGINT    NOT NULL,
    redeemed_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_redemptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_redemptions_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT uk_redemptions_order UNIQUE (order_id)
);

CREATE INDEX IF NOT EXISTS idx_redemptions_coupon ON coupon_redemptions (coupon_id);
CREATE INDEX IF NOT EXISTS idx_redemptions_coupon_user ON coupon_redemptions (coupon_id, user_id);

-- The coupon applied at checkout, snapshotted on the order itself so it still
-- shows correctly even if the coupon is later edited or archived.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(32);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_paise BIGINT NOT NULL DEFAULT 0;
