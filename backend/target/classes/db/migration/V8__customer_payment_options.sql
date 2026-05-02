-- Merchant-configurable customer payment methods (checkout + salon booking)
ALTER TABLE shop_settings
  ADD COLUMN accept_customer_eft TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN accept_customer_cash TINYINT(1) NOT NULL DEFAULT 1;
