-- Historical reference only. Flyway is inactive (pom: Hibernate DDL auto).
-- Applied at startup by PeachSchemaMigration (idempotent) — do not run manually.

-- Merchant subscription billing: pay online via Peach Hosted Checkout.
ALTER TABLE merchant_subscriptions
  ADD COLUMN peach_checkout_id VARCHAR(128) NULL,
  ADD COLUMN peach_merchant_transaction_id VARCHAR(64) NULL,
  ADD COLUMN peach_payment_method VARCHAR(16) NULL;

CREATE INDEX idx_merchant_subscriptions_peach_merchant_tx ON merchant_subscriptions (peach_merchant_transaction_id);
CREATE INDEX idx_merchant_subscriptions_peach_checkout ON merchant_subscriptions (peach_checkout_id);
