-- ---------------------------------------------------------------------------
-- Sri Vinayaka Bakery Home :: distance-based delivery price + live tracking
-- Purely additive. distance_km is the store -> delivery pin distance used to
-- price the order; the partner_* columns hold the courier's last reported
-- position while the order is out for delivery. Pricing values live in
-- app_settings (delivery.pricing.*), so no new table is needed.
-- ---------------------------------------------------------------------------

ALTER TABLE orders ADD COLUMN IF NOT EXISTS distance_km DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS partner_latitude DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS partner_longitude DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS partner_location_at TIMESTAMP;
