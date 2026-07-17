-- Historical reference only. Flyway is inactive (pom: Hibernate DDL auto).
-- Applied at startup by PeachSchemaMigration (idempotent) — do not run manually.

-- Replace customer manual EFT offer flag with In-App Peach; keep legacy payment_method='eft' rows readable.
ALTER TABLE shop_settings
  CHANGE COLUMN accept_customer_eft accept_customer_peach TINYINT(1) NOT NULL DEFAULT 1;

ALTER TABLE orders
  ADD COLUMN peach_checkout_id VARCHAR(128) NULL,
  ADD COLUMN peach_merchant_transaction_id VARCHAR(64) NULL;

ALTER TABLE salon_bookings
  ADD COLUMN peach_checkout_id VARCHAR(128) NULL,
  ADD COLUMN peach_merchant_transaction_id VARCHAR(64) NULL;

CREATE INDEX idx_orders_peach_merchant_tx ON orders (peach_merchant_transaction_id);
CREATE INDEX idx_orders_peach_checkout ON orders (peach_checkout_id);
CREATE INDEX idx_bookings_peach_merchant_tx ON salon_bookings (peach_merchant_transaction_id);
CREATE INDEX idx_bookings_peach_checkout ON salon_bookings (peach_checkout_id);
