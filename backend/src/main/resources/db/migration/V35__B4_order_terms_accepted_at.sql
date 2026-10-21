-- V35 (B4): checkout terms acceptance. POST /api/checkout now requires acceptTerms = true (400 TERMS_NOT_ACCEPTED otherwise);
-- the moment the buyer accepted is stored on every order created by that checkout. Nullable: orders placed before V35 (B4)
-- (and orders from other channels) have no value. Additive, idempotent.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS terms_accepted_at timestamp with time zone;

COMMENT ON COLUMN orders.terms_accepted_at IS 'Time the buyer accepted the checkout terms (POST /api/checkout acceptTerms=true); NULL = before V35 (B4) / other channel';