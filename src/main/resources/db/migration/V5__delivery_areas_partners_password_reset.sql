-- ---------------------------------------------------------------------------
-- Sri Vinayaka Bakery Home :: delivery PIN codes, delivery partners, password reset
-- Purely additive, so it applies cleanly on top of a database already
-- carrying V1-V4 and live data. Nothing here changes existing behaviour until
-- an admin switches the delivery restriction on.
-- ---------------------------------------------------------------------------

-- PIN codes the store delivers to. Only consulted while the restriction
-- switch below is on.
CREATE TABLE IF NOT EXISTS delivery_pincodes (
    id         BIGSERIAL PRIMARY KEY,
    pincode    VARCHAR(6)   NOT NULL,
    area_name  VARCHAR(120),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_delivery_pincodes_pincode UNIQUE (pincode)
);

-- Small key/value store for admin-editable switches. A missing
-- 'delivery.restrict-to-pincodes' row means the restriction is OFF, so an
-- upgraded shop keeps taking orders from everywhere until an admin opts in.
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key   VARCHAR(64)  PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Delivery partners are users with role DELIVERY_PARTNER (fits VARCHAR(20)).
-- An admin hands an order to one; if that account is ever removed the order
-- simply becomes unassigned again.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_partner_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_delivery_partner'
    ) THEN
        ALTER TABLE orders ADD CONSTRAINT fk_orders_delivery_partner
            FOREIGN KEY (delivery_partner_id) REFERENCES users (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_orders_delivery_partner ON orders (delivery_partner_id);

COMMENT ON COLUMN users.role IS 'USER | ADMIN | SUPER_ADMIN | DELIVERY_PARTNER';

-- One-time "forgot password" links. Only the SHA-256 of the token is kept.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_password_reset_user ON password_reset_tokens (user_id);
