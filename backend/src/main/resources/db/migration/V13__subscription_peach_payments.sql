-- Historical reference only. Flyway is inactive (pom: Hibernate DDL auto).
-- Applied at startup by PeachSchemaMigration (idempotent) — do not run manually.

-- Per-checkout ledger for merchant subscription Peach Hosted Checkout (CARD / PAYBYBANK).
CREATE TABLE IF NOT EXISTS subscription_peach_payments (
  id CHAR(36) NOT NULL PRIMARY KEY,
  tenant_id CHAR(36) NOT NULL,
  plan_tier VARCHAR(16) NOT NULL,
  amount DECIMAL(12, 2) NOT NULL,
  currency VARCHAR(8) NOT NULL DEFAULT 'ZAR',
  status VARCHAR(24) NOT NULL,
  peach_payment_method VARCHAR(16) NOT NULL,
  peach_merchant_transaction_id VARCHAR(64) NOT NULL,
  peach_checkout_id VARCHAR(128) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  completed_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_subscription_peach_merchant_tx UNIQUE (peach_merchant_transaction_id)
);

CREATE INDEX idx_subscription_peach_tenant_status ON subscription_peach_payments (tenant_id, status);
CREATE INDEX idx_subscription_peach_checkout ON subscription_peach_payments (peach_checkout_id);
