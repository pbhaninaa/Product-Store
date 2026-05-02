-- In-store cash: merchant verifies payment using a code shown to the customer after checkout / booking.

ALTER TABLE orders
  ADD COLUMN cash_payment_code VARCHAR(16) NULL;

ALTER TABLE salon_bookings
  ADD COLUMN cash_payment_code VARCHAR(16) NULL;
