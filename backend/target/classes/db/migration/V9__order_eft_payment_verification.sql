-- EFT store orders: proof upload, auto reference match, or manual merchant confirmation (Wheel Hub–style flow).

ALTER TABLE orders
  ADD COLUMN payment_verification_state VARCHAR(32) NOT NULL DEFAULT 'not_applicable',
  ADD COLUMN payment_proof_path TEXT NULL,
  ADD COLUMN payment_reference_declared VARCHAR(512) NULL;

-- Existing unpaid EFT orders should require proof before merchant can confirm.
UPDATE orders
SET payment_verification_state = 'awaiting_proof'
WHERE payment_method = 'eft'
  AND status = 'pending_payment';
