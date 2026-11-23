# Architecture reference — Product-Store

**Last updated:** November 2026

Multi-tenant storefront (shops and salons) + merchant admin + platform support console.

```
 Shopper / merchant / support            Mobile app (React Native WebView, flavors sit/uat/prod)
            │                                         │ loads the hosted SPA, injects API origin
            ▼                                         ▼
   frontend/  Vue 2 + Vuetify SPA (Vercel) ──── HTTPS JSON + STOMP websocket (/ws) ────┐
                                                                                        ▼
   backend/  Spring Boot 3 / Java 17 (Railway, Docker) ── JPA/Hibernate ──► MySQL (H2 on SIT/tests)
            │                     │                         │
            ▼                     ▼                         ▼
       Paystack (webhook)    SendGrid email           Twilio WhatsApp
```

## Tenancy

- Each store is a `TenantEntity` with a unique **slug**. URLs carry it: SPA `/m/<slug>/…`, API
  `/api/public/m/<slug>/…` (anonymous) and `/api/m/<slug>/admin/…` (merchant).
- `TenantContextFilter` + `PathBasedTenantResolver` resolve the tenant from the path; `TenantAccessService`
  checks the caller's membership for that tenant.
- Users can hold memberships (`MembershipEntity`) with a `Role` per tenant: `MERCHANT_OWNER`, `MERCHANT_STAFF`;
  platform roles `SUPPORT_USER`, `PLATFORM_ADMIN` have no tenant; shoppers are `CLIENT`.

## Backend packages (`com.productstore.platform`)

| Package | Contents |
|---------|----------|
| `controllers` | REST controllers. `Public*` = anonymous storefront; `Admin*` = merchant admin; `Support*` / `PlatformAdmin*` = support console; `Client*` = shopper account; `PaystackController` = payment webhook and configuration check; `HealthController` |
| `services` | Business logic: `CheckoutService` (orders, stock), `SalonBookingService`, `MerchantSubscriptionService` (trial, plans), `PaystackPaymentService`, `MerchantNotificationService` (email/WhatsApp), `SupportConsoleService`, `ReferralService`, … |
| `services/auth` | JWT (`JwtService`, 12-hour lifetime), password hashing, `Role` |
| `services/multitenancy` | Tenant resolution from the URL |
| `entities`, `repositories` | JPA entities and Spring Data repositories |
| `config` | Payment properties, `EnvFlags` (blank-safe booleans), `DeployedEnvironmentValidator` (UAT/PROD fail-fast), Railway datasource handling, websocket, startup schema migrations |
| `security` | Websocket JWT handshake |
| `util` | Webhook signature helpers, email helpers, payment-method helpers |

## Key flows

- **Orders:** `pending_payment` → `paid` (cash confirmed by merchant, EFT proof matched/confirmed, or Paystack webhook)
  → fulfilment `processing` → `ready` → `completed`; or `cancelled` while unpaid. Stock drops once, on payment.
- **Paystack:** the API starts a Paystack transaction and returns a hosted payment page URL; the browser is redirected
  there and comes back to `/m/<slug>/payment/return` (subscriptions: `/payment/return`). Paystack calls
  `POST /api/payments/paystack/webhook`; the signature (`x-paystack-signature`, HMAC SHA512 of the raw body with the secret key)
  is checked, and the order/booking row is locked (`lockBy…` repository methods) so retries and concurrent webhooks settle it once.
  The return page also calls the payment-status endpoint, which asks Paystack to verify the transaction if the webhook is late.
  Split payments to providers are not built yet: everything is paid into the single platform Paystack account.
- **Subscriptions:** 7-day trial from store creation, then plans `STARTER` / `STANDARD` / `PREMIUM` paid via Paystack.
  Subscription payments use the same webhook. `ActiveSubscriptionAdminFilter` gates merchant admin when unpaid.
- **Realtime:** STOMP over `/ws` (`/topic`, `/queue`) pushes in-app notifications.

## Data and configuration

- Schema: Hibernate `ddl-auto=update` + idempotent startup migrations; no Flyway.
- Profiles: `sit` (H2, demo seed), `local` (your MySQL, gitignored file), `uat`, `prod`, `test` (automated tests).
- Variables: [../backend/ENV.md](../backend/ENV.md).

## Frontend (`frontend/src`)

| Folder | Contents |
|--------|----------|
| `router/index.js` | Routes and role guards (`requiresClient`, `requiresSupportConsole`) |
| `services/` | `api.js` (fetch wrapper, base URL, 401 → session expiry), `auth.js` (JWT session), `cart.js` (in-memory cart), domain API clients |
| `views/` | Storefront, checkout, payment return, client account; `views/admin/` merchant admin; `views/support/` support console |
| `components/`, `utils/` | Shared UI and helpers |
