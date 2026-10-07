-- ---------------------------------------------------------------------------
-- Sri Vinayaka Bakery Home :: readable address of the delivery pin
-- Purely additive. Google's reverse-geocoded address for the pin a customer
-- dropped at checkout, shown to the delivery partner as a second reference
-- next to the typed address. Null when no pin or no Maps key.
-- ---------------------------------------------------------------------------

ALTER TABLE orders ADD COLUMN IF NOT EXISTS pin_address VARCHAR(300);
