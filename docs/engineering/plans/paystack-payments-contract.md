# Plan: Replace the previous payment providers with Paystack

| Field | Value |
|-------|-------|
| Requested by | CTO |
| Date | 2026-11-22 |
| Branch | `feature/paystack-payments` (from SIT) |
| Status | In progress |

## CTO decisions
1. Remove **the previous payment providers** entirely. Paystack is the only online payment provider.
2. Nothing may refer to the previous payment providers in code, naming, config, UI, tests or docs. The only exception is the one-time
   database migration class (it must name the old columns to rename them); it is deleted in a follow-up release once
   SIT, UAT and PROD have all run it.
3. Database columns/tables are **renamed with a one-time migration that keeps all data** (approved by the CTO).
4. First version: orders, bookings and merchant subscriptions are paid into the **single platform Paystack account**
   (same as the previous provider today). Split payments come later, once the commission is decided.

## Naming rule
- Data model and API: **provider neutral** ("online payment"), so a future provider change needs no renames.
- Integration code: **Paystack specific** (`PaystackPaymentService`, `PaystackProperties`, `PaystackController`, ...).

## Database renames (old → new)

The old names live only in the one-time migration class (`LegacyPaymentSchemaMigration`), which is the only place allowed to name them.

| Table | Old | New |
|---|---|---|
| `orders` | old payment method column | `online_payment_channel` |
| `orders` | old merchant transaction id column | `online_payment_reference` |
| `orders` | old checkout id column | `online_payment_provider_id` |
| `salon_bookings` | same three columns | same three new names |
| `merchant_subscriptions` | same three columns | same three new names |
| `shop_settings` | old accept-customer column | `accept_customer_online` |
| old subscription payments table (renamed) | | `subscription_online_payments` |
| `subscription_online_payments` | the same three old columns | `online_payment_channel`, `online_payment_reference`, `online_payment_provider_id` |
| indexes / unique keys carrying the old provider name | | same name with the old provider name replaced by `online` |

Value migrations:
- `orders.payment_method` and `salon_bookings.client_payment_method`: the previous providers' values → `online`.
- `subscription_online_payments.status`: the old pending status → `pending_online` (check entity constants for any other old status values).
- `platform_features`: the previous providers' row → removed; new key `PAYSTACK` (bootstrapped enabled).
- Channel values stay `CARD` / `EFT`.

## API contract (backend ↔ web ↔ mobile)

### Enum values
- Order `paymentMethod` / booking `clientPaymentMethod` / request `paymentMethod`: `cash_store`, `eft`, **`online`**.
- Channel: `onlinePaymentChannel`: `CARD` | `EFT`.

### Start-payment responses (all of them)
```json
{ "needsOnlinePayment": true, "paymentId": "<order/booking/subscription-payment id>", "paymentRedirectUrl": "https://checkout.paystack.com/..." }
```
The browser does `window.location.href = paymentRedirectUrl`. No form POST any more.

### Endpoints
| Purpose | Method + path | Request | Response |
|---|---|---|---|
| Place order | `POST /api/public/m/{slug}/checkout/orders` (unchanged path) | `paymentMethod: "online"`, `onlinePaymentChannel` | existing fields + start-payment fields when online |
| Book service | existing salon booking POST (unchanged path) | `paymentMethod: "online"`, `onlinePaymentChannel` | existing fields + start-payment fields when online |
| Customer pays existing order | `POST /api/clients/me/orders/{orderId}/online-checkout` | none | `{ paymentId, paymentRedirectUrl }` |
| Customer pays existing booking | `POST /api/clients/me/bookings/{bookingId}/online-checkout` | none | `{ paymentId, paymentRedirectUrl }` |
| Merchant subscription | `POST /api/m/{slug}/admin/subscription/online-checkout` | `{ onlinePaymentChannel }` | `{ paymentId, paymentRedirectUrl }` |
| Order payment status | `GET /api/public/m/{slug}/checkout/orders/{orderId}/payment-status?customerEmail=` | | same shape as today's status response, no previous-provider keys |
| Booking payment status | `GET /api/public/m/{slug}/salon/bookings/{bookingId}/payment-status?customerEmail=` | | same |
| Paystack webhook | `POST /api/payments/paystack/webhook` | Paystack event | 200 |
| Provider configured | `GET /api/payments/paystack/configured` | | `{ configured: boolean }` |

Status endpoints call Paystack "verify transaction" when the record is still pending, so the return page confirms even if the webhook is late.

### Shop settings
- Public shop settings: `acceptCustomerOnline` (bool), `onlinePaymentConfigured` (bool). The previous provider fields are removed.
- Admin store settings request/response: `acceptCustomerOnline`.

### Error codes (backend `IllegalArgumentException` messages the web maps to text)
`online_payment_not_accepted`, `online_payment_channel_required`, `invalid_online_payment_channel`,
`online_payment_channel_not_applicable`, `not_online_order`, `not_online_booking`, `online_payment_unavailable`.

### Return page
- Routes: `/payment/return` and `/m/:merchantSlug/payment/return`.
- `callback_url` query: `kind=order|booking|subscription`, plus `id` + `email` (order/booking) or `paymentId` + `merchant` (subscription).
  Paystack appends `trxref` and `reference`.
- The previous providers' return routes are removed.

### Labels
- In-app method label: `Paystack · Card`, `Paystack · Instant EFT`, or `Paystack`.

## Configuration
| Env var | Meaning |
|---|---|
| `PAYSTACK_ENABLED` | `true` to offer online payment |
| `PAYSTACK_SECRET_KEY` | Paystack secret key (`sk_test_...` / `sk_live_...`); also signs webhooks |
| `PAYSTACK_BASE_URL` | default `https://api.paystack.co` |

All variables of the previous payment providers are removed.

## Cut-over risk
Any payment with the previous provider still unpaid at deploy time can no longer be confirmed automatically (its webhook is gone). Deploy at a quiet time
and check for pending online orders/bookings first; reconcile any by hand.
