-- Historical reference only. Flyway is inactive (pom: Hibernate DDL auto).
-- Applied at startup by PeachSchemaMigration (idempotent) — do not run manually.

-- Durable one-time 30-day merchant free trial (UTC instants from tenant created_at).
ALTER TABLE merchant_subscriptions
  ADD COLUMN trial_start_at TIMESTAMP(6) NULL,
  ADD COLUMN trial_end_at TIMESTAMP(6) NULL,
  ADD COLUMN trial_dates_backfilled TINYINT(1) NOT NULL DEFAULT 0;
