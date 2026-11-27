-- Add account_type to shop_settings for Paystack South African account validation
-- This field stores whether the payout account is "personal" or "business"
-- Required by Paystack's /bank/validate endpoint (SA accounts only)

ALTER TABLE shop_settings ADD COLUMN payout_account_type VARCHAR(20);

-- Set default to 'personal' for existing verified accounts
UPDATE shop_settings
SET payout_account_type = 'personal'
WHERE payout_account_verified_name IS NOT NULL AND payout_account_verified_name != '';

-- Add comment
COMMENT ON COLUMN shop_settings.payout_account_type IS 'Account type for Paystack validation: personal or business. Required for SA accounts.';
