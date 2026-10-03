-- ---------------------------------------------------------------------------
-- Sri Vinayaka Bakery Home :: Google Maps - delivery pin + store location
-- Purely additive. The dropped pin from the checkout map (when Google Maps is
-- configured) is stored alongside the address text; null when no pin was
-- dropped (manual-address checkout keeps working exactly as before).
-- The store's own location is an admin-editable value, kept in app_settings
-- (store.location.lat / .lng / .address) rather than a new table.
-- ---------------------------------------------------------------------------

ALTER TABLE orders ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
