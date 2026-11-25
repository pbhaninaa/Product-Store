# Remove Manual EFT: backend API change note

Decision: CTO, 2026-11-23, "remove manual eft". Branch `feature/remove-manual-eft`.

Removed: (a) customer Manual EFT (bank transfer + proof upload) at checkout and salon booking;
(b) merchant subscription EFT (bank transfer + proof approved by support).
Kept: Paystack online payment (`paymentMethod: "online"`, `onlinePaymentChannel: "CARD" | "EFT"`, where
`EFT` means Paystack Instant EFT) and cash in store. No DB columns or tables are dropped.

## Customer checkout and salon booking

| Call | Change |
| --- | --- |
| `POST /api/public/m/{slug}/checkout/orders` | `paymentMethod: "eft"` now always returns **400 `{"error":"eft_not_accepted"}`**. No order is created and no stock or promotion use is held. Response no longer ever contains `needsEftProof`. |
| `POST /api/public/m/{slug}/salon/bookings` | `paymentMethod: "eft"` now always returns **400 `{"error":"eft_not_accepted"}`**, also when the shop has only one other method enabled (before, a single-method shop silently ignored the requested method). Response field `needsEftProof` is **removed**. |
| `POST .../checkout/orders/{id}/eft-proof` and `POST .../salon/bookings/{id}/eft-proof` | Kept **only** for orders/bookings created as EFT before this change and still in `awaiting_proof`. New EFT rows can no longer exist, so the frontend should stop calling these. Existing error codes unchanged (`not_eft_order`, `not_eft_booking`, `proof_not_expected`, `proof_already_submitted`, ...). |

Error code to show: `eft_not_accepted` (400). Suggested copy: "Bank transfer is no longer available. Pay online
with Paystack or in store."

If a shop had turned off both cash and online (EFT only), it now has no customer payment method: checkout returns
`cash_not_accepted` / `online_payment_not_accepted` and salon booking returns `payment_not_configured` (400).

## Public shop data

`GET /api/public/m/{slug}/shop-settings`:

- **Removed**: `eftBankInstructions`, `bankName`, `bankAccountHolder`, `bankAccountNumber`, `bankBranchCode`.
  The bank fields are now the merchant's payout details and are not public. Do not show them on invoices.
- `acceptCustomerEft`: **deprecated, always `false`**. Kept for one release because older clients treat a missing
  value as `true`. Do not read it; it will be removed later.

Nearby merchant search cards (`acceptCustomerEft`): same, always `false`, deprecated.

## Merchant admin store settings

- `GET /api/m/{slug}/admin/store/settings`: `acceptCustomerEft` and `eftBankInstructions` are **no longer returned**.
  `bankName`, `bankAccountHolder`, `bankAccountNumber`, `bankBranchCode` are still returned (payout details).
- `PUT /api/m/{slug}/admin/store/banking`: `acceptCustomerEft` and `eftBankInstructions` are **no longer accepted**
  (silently ignored if sent). Bank fields, `acceptCustomerOnline`, `acceptCustomerCash` unchanged.
  `400 payment_options_required` now fires when **online and cash are both off**.
- The "Manual EFT" toggle and "EFT instructions" field should be removed from the Store settings screen. Bank
  fields should be labelled as payout details.

## Historical data and in-flight review (unchanged endpoints)

- Admin/support order and booking lists and detail still show EFT rows (`paymentMethod`/`clientPaymentMethod`
  `eft`, `paymentVerificationState`, proof link). Keep rendering them read-only with an "EFT (legacy)" label.
- Order EFT in `manual_pending`: still confirmed via `POST /api/m/{slug}/admin/orders/{id}/confirm-payment`.
- Booking EFT in `manual_pending`: `POST /api/m/{slug}/admin/salon/bookings/{id}/eft-payment-decision`
  (`{"decision":"approve"|"reject"}`) still works.

## Merchant subscription

- `POST /api/m/{slug}/admin/subscription/payment-proof`: **410 `manual_eft_disabled`** (already the case on SIT).
- `POST /api/support/subscriptions/{tenantId}/approve-proof` and `/reject-proof`: **410
  `subscription_proof_mutation_disabled`** (already the case on SIT; manual activation without payment stays off).
- `GET /api/support/subscriptions/pending-proofs` and `GET .../{tenantId}/proof-file`: still readable (history).
- Paying: `POST /api/m/{slug}/admin/subscription/online-checkout` with `{"onlinePaymentChannel":"CARD"|"EFT"}`
  (Paystack) is the only path.
- `GET /api/m/{slug}/admin/subscription/me`: `needsPayment` (and its alias `needsPaymentProofUpload`) no longer
  depends on `paymentProofStatus`. Before, an expired trial or expired paid period whose last status was
  `APPROVED` (every provisioned merchant) or a stuck legacy `PENDING` proof reported `needsPayment: false`, which
  hid the Paystack pay card. Now an expired subscription with a plan always reports `needsPayment: true`.
  `paymentProofPendingReview`, `paymentProofStatus` and `platformBankingConfigured` are legacy and should not
  drive any UI.
