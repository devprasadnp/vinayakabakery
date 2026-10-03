-- ---------------------------------------------------------------------------
-- Sri Vinayaka Bakery Home :: Paytm Dynamic QR support
-- Purely additive. Holds the UPI string behind a gateway-generated QR (Paytm's
-- "qrData") so /orders/{orderNumber}/qr-code.png can render it; null for every
-- payment made through a gateway that hosts its own checkout page (PhonePe) or
-- through the manual UPI-QR flow.
-- ---------------------------------------------------------------------------

ALTER TABLE payments ADD COLUMN IF NOT EXISTS qr_payload TEXT;
