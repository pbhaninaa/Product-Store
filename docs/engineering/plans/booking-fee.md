# Plan: Booking fee for cash bookings

| Field | Value |
|-------|-------|
| Requirement | [requirements/booking-fee.md](../requirements/booking-fee.md) (agreed 2026-11-22) |
| Branch | `feature/provider-payouts` |
| Date | 2026-11-24 |
| Status | Implemented |

## 1. Summary
A provider sets a booking fee (whole percent, 0 to 50) in the store settings. A cash-at-shop booking
whose fee applies is held `pending` with `bookingFeeStatus=awaiting_payment` and the customer is sent to
Paystack for the fee only, on the existing checkout rail (`online_payment_attempts`, kind `BOOKING`,
reference `BKG...`). Paid within 2 hours: the fee is `paid` and the booking keeps its slot with the
remainder due at the shop. Not paid: the existing unpaid expiry job cancels it and frees the slot. Staff
enter the handover code to collect only the remainder, which marks the booking fully paid (`confirmed`).
Cancellations refund or keep the fee server side; refunds go through a new Paystack Refund API client with
a refund ledger, retries and support flagging.

## 2. Decisions
| # | Decision | Why |
|---|----------|-----|
| D1 | Fee percent is a whole number 0..50 (`shop_settings.booking_fee_percent`, null = 0). Out of range or fractional: `booking_fee_percent_out_of_range` (400). Endpoint `PUT /api/m/{slug}/admin/store/booking-fee`, permission `STORE_SETTINGS` (owner always has it). | FR-1, BR-1, AC-1..3. Whole percent keeps the UI and rounding simple. |
| D2 | Fee = `round_half_up(amountZar x percent / 100, 2)`, remainder = `amountZar - fee`. `amountZar` (price after promotion) is unchanged, so payroll (`EmployeePayRules`) and receipts keep their meaning. Fee, percent and remainder are fixed on the booking when it is made. | NFR-1, BR-7, edge case 3. |
| D3 | The fee applies only when: method `cash_store`, percent > 0, online payment available on the platform (Paystack configured, features on, not cash-only demo mode) and fee >= the Paystack minimum charge. Otherwise the cash booking behaves exactly as today. | FR-2, FR-3, BR-2, open question 1. |
| D4 | Paystack minimum ZAR charge: R1.00 (Paystack "supported currencies": ZAR minimum 1.00). Configurable as `paystack.minimum-charge-cents` (env `PAYSTACK_MINIMUM_CHARGE_CENTS`, default `100`). Fee below it: no online step (proposed default of open question 1). | Edge case 6. |
| D5 | After the fee is paid the booking status stays `pending` (many places read `confirmed` as "fully paid": payroll credit, paid flag, delete guard). `bookingFeeStatus=paid` is what the customer sees as "booking confirmed, R800 due at the shop". | Keeps existing status semantics. |
| D6 | Staff cannot record the cash remainder while the fee is still unpaid (`booking_fee_unpaid`). The customer can still pay from their booking page; otherwise the booking expires after 2 hours. | Avoids a late fee payment for a booking already paid in full. |
| D7 | Unpaid fee expiry reuses `UnpaidOrderExpiryJob` (every 5 minutes). TTL `app.bookings.unpaid-fee-expiry`, default = `app.orders.unpaid-expiry.in-app` (PT2H). Sets status `cancelled`, `bookingFeeStatus=not_paid`, releases the promotion. | FR-5, BR-3. |
| D8 | Customer self cancel (new): public `POST .../salon/bookings/{id}/cancel` with the booking id (UUID) and the customer email, and `POST /api/clients/me/bookings/{id}/cancel` for the signed in owner. Only `cash_store` bookings that are `pending`/`confirmed` and not started. In app paid bookings keep today's behaviour (contact the shop): no refund rule exists for them. | FR-8, US-4. |
| D9 | Refund rule (server side only): customer cancels with `startAt - now >= 24h` -> refund (`customer_cancellation`); less -> kept (`late_cancellation`); no-show -> kept (`no_show`); provider cancels at any time -> refund (`provider_cancellation`). Exactly 24h refunds. | BR-4..6, edge cases 1, 2. |
| D10 | No-show (new): `POST /api/m/{slug}/admin/salon/bookings/{id}/no-show`, permissions `BOOKINGS` + `CANCEL_BOOKINGS`, only `pending`/`confirmed` bookings whose start time has passed. Sets `cancelled`, `no_show_at`, keeps a paid fee. | US-4 AC-3, requirements section 9. |
| D11 | Refund ledger `online_payment_refunds` (unique per payment attempt = DB guard against a second refund). Attempt outcomes `REFUND_PENDING` and `REFUNDED` added (VARCHAR column, no schema change). Refund submitted to Paystack after the DB commit (`POST /refund`, full fee amount, by transaction reference). | NFR-4, FR-8. |
| D12 | Refund failures: the refund stays `PENDING`, is flagged for support (`flagged_for_support`, log marker `BOOKING_FEE_REFUND_FAILED`) and is retried by `BookingFeeRefundJob` with backoff `min(5 min x 2^tries, 6 h)`. Before every retry the transaction is checked with Paystack: if it already shows a reversal, no second refund is sent. Paystack "fully reversed" answers also count as done. The customer sees "refund in progress". | Edge case 5, NFR-4. |
| D13 | Refund webhooks `refund.processed` / `refund.failed` (same HMAC check) update the refund: processed -> `PROCESSED`; failed -> back to `PENDING`, flagged, retried. Replays are no-ops. `refund.pending`/`refund.processing` are ignored. | NFR-4. |
| D14 | A fee paid after its booking was cancelled (2 hour window or otherwise) is refunded automatically (reason `payment_after_cancellation`); the booking stays cancelled. Settlement outcome `REFUNDING`. | Edge case 4. |
| D15 | Audit: `booking_fee_events` rows for every fee payment, refund request, refund submit/failure/processing, support flag and kept fee, with time, amount, reason and payment reference. | NFR-3. |
| D16 | Customer facing receipt lines are built server side (`receiptLines`: `booking_fee_online`, `booking_fee_due_online`, `paid_at_shop`, `due_at_shop`, `paid_online`, `fee_refunded`, `fee_refund_in_progress`, `fee_kept`, `total`) and labelled by the frontend. | FR-7, single source of truth. |

## 3. Provider payouts interaction
- A paid fee attempt is an ordinary `PAID` `BOOKING` attempt, so it is payable to the provider as soon as it
  is paid (commission on its gross, Paystack fee captured as today).
- Payable rule for bookings becomes: booking not cancelled, **or** `booking_fee_status = kept`, **or**
  `no_show_at` set. A kept fee (late cancellation, no-show) therefore stays owed to the provider; an in app
  paid booking marked no-show stays payable (the provider keeps it, as before when it was left confirmed).
- A refunded or refunding fee is not payable: its attempt leaves `PAID` (`REFUND_PENDING`/`REFUNDED`).
- **Already released fee that must be refunded:** the payout is never altered (provider-payouts edge case 6).
  The app does **not** auto refund money that has already been paid out to the provider: the attempt is set
  to the existing `NEEDS_REFUND` state ("a person must refund"), the refund row is `MANUAL` and flagged, the
  marker `ONLINE_PAYMENT_NEEDS_REFUND` is logged with the payout id, and the customer sees "refund in
  progress". When support refunds it in the Paystack dashboard, Paystack's `refund.processed` webhook marks
  it refunded. Clawback from the provider stays the existing follow-up.

## 4. API changes (additive)
| Endpoint | Change |
|----------|--------|
| `PUT /api/m/{slug}/admin/store/booking-fee` | New: `{ "bookingFeePercent": 20 }` |
| `GET /api/m/{slug}/admin/store/settings` | Adds `bookingFeePercent` |
| `GET /api/public/m/{slug}/shop-settings` | Adds `bookingFeePercent`, `bookingFeeActive`, `bookingFeeMinimumZar` |
| `POST /api/public/m/{slug}/salon/bookings` | Cash with fee: `onlinePaymentChannel` optional (CARD default); response adds `bookingFee` and the checkout fields (`needsOnlinePayment`, `paymentRedirectUrl`) |
| `GET .../salon/bookings/{id}`, `.../payment-status`, `/api/clients/me/bookings` | Add `bookingFee` (amounts, status, refundable now) and `receiptLines`; return page `paid` = fee paid for fee bookings |
| `POST .../salon/bookings/{id}/cancel`, `POST /api/clients/me/bookings/{id}/cancel` | New customer cancel; response `{ status, bookingFeeOutcome, bookingFee }` |
| `POST /api/m/{slug}/admin/salon/bookings/{id}/no-show` | New |
| `GET /api/m/{slug}/admin/salon/bookings` | Rows add `bookingFeeZar`, `bookingFeePercent`, `bookingFeeStatus`, `bookingFeeReason`, `amountDueZar`, `noShow`, `receiptLines` |
| `POST /api/payments/paystack/webhook` | Also handles `refund.processed` and `refund.failed` |

## 5. Schema (Hibernate `ddl-auto=update`; every new column nullable)
- `shop_settings.booking_fee_percent INT NULL`
- `salon_bookings`: `booking_fee_percent INT`, `booking_fee_zar DECIMAL(12,2)`, `booking_fee_status VARCHAR(20)`,
  `booking_fee_reason VARCHAR(32)`, `booking_fee_paid_at`, `booking_fee_outcome_at`, `cancelled_at`, `no_show_at`
- `online_payment_refunds` (new table), `booking_fee_events` (new table)
- Enum columns use `@JdbcTypeCode(VARCHAR)` so new values never need a schema change.

## 6. Error codes (new)
`booking_fee_percent_out_of_range`, `booking_fee_unpaid`, `booking_not_cancellable`, `no_show_too_early`,
`booking_fee_paid_cancel_first`.

## 7. Tests
Backend integration `BookingFeeIntegrationTest` (Paystack client mocked, signed webhooks, mutable clock)
covers every AC and edge case; unit tests for the policy (rounding, 24h boundary, range), the refund
service (retry, idempotency, flag), the HTTP refund client and the payout rule. Frontend vitest for the fee
utilities, labels and the settings/booking/admin source guards.

## 8. Out of scope / follow-ups
Clawback of a fee already paid out (existing payouts follow-up); customer self cancel of in app paid
bookings; split payments (not used: the platform account takes the fee, payouts pay it to the provider).
