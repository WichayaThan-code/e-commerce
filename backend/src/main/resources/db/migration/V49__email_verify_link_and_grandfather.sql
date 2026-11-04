-- Email verification gate: link token on OTP rows + grandfather existing accounts (admins / pre-gate users).

ALTER TABLE otp_verifications
    ADD COLUMN IF NOT EXISTS verify_link_token_hash VARCHAR(64);

COMMENT ON COLUMN otp_verifications.verify_link_token_hash IS 'SHA-256 of one-time email verify link token (EMAIL_VERIFY only)';

-- Existing accounts (including platform admins / seed) remain usable after the gate turns on.
UPDATE users
SET email_verified_at = CURRENT_TIMESTAMP
WHERE email_verified_at IS NULL
  AND deleted_at IS NULL;
