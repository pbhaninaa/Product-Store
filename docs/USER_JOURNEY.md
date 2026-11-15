# Product-Store — end-to-end user journey

**Last updated:** November 2026

How a merchant, a shopper and the platform team move through the system, with the API behind each step.
The automated versions of these journeys are `StorePayFastE2ETest` (backend) and `storeJourney.cy.ts`
(browser) — see [../backend/TESTING.md](../backend/TESTING.md).

## Verification (local)

```bash
cd backend && mvn test -Dtest=StorePayFastE2ETest     # API journeys
cd frontend && npm run test:e2e                       # browser journey (needs SIT stack running)
```

## Roles

| Role | Who | Signs in at |
|------|-----|-------------|
| `MERCHANT_OWNER` | Store owner; billing, team, settings | `/m/<slug>/admin` |
| `MERCHANT_STAFF` | Store team member: orders, bookings, alerts, own income (products, settings, salon setup, team and billing are owner-only) | `/m/<slug>/admin` |
| `CLIENT` | Shopper with an account (optional — guests can check out) | `/account/login` |
| `SUPPORT_USER` | Platform support, permission-scoped | `/m/platform/admin?support=1` |
| `PLATFORM_ADMIN` | Platform owner, all support permissions | `/m/platform/admin?support=1` |

Stores are tenants addressed by **slug** in the URL: storefront `/m/<slug>`, API `/api/public/m/<slug>/…`
(public) and `/api/m/<slug>/admin/…` (merchant).

## 1. Merchant signs up

1. `/signup` → `POST /api/auth/register-merchant` (store name, owner email, password; slug generated from the name).
2. The owner is signed in and lands in merchant admin. A one-time **7-day free trial** with full access starts at
   store creation (UTC dates stored on the subscription; never reset).

## 2. Merchant sets up the store

1. **Products:** `POST /api/m/<slug>/admin/products` (name, category, price, stock, image).
2. **Store settings:** which payment methods customers may use — cash, manual EFT (needs banking details),
   PayFast — plus delivery fees and opening hours.
3. Salon stores also add services, staff and availability.

## 3. Shopper buys

1. Browses `/m/<slug>` (or finds stores via `/discover`), adds products to the cart (held in the browser
   session), opens **Cart** → checkout.
2. Enters name, email, phone; chooses **pickup** or **delivery**; chooses a payment method.
3. `POST /api/public/m/<slug>/checkout/orders` creates the order as **`pending_payment`**.

| Payment | What happens next | Becomes `paid` when |
|---------|-------------------|---------------------|
| **Cash** | Shopper pays in store / to the courier | Merchant clicks confirm payment (`…/orders/{id}/confirm-payment`) |
| **Manual EFT** | Shopper transfers and uploads proof (`…/orders/{id}/eft-proof`) | Reference auto-matches, or the merchant confirms |
| **PayFast** (card / Instant EFT) | Browser posts the signed form to PayFast's hosted page, then returns to `/m/<slug>/payfast/return` | PayFast's signed ITN reaches `/api/payments/payfast/webhook` (amount and reference checked; duplicates ignored) |

Stock is reduced once, when the order becomes paid. The return page polls
`GET /api/public/m/<slug>/checkout/orders/{id}/payfast-status` until the ITN has arrived.

4. Shoppers with an account see the order in `/account/history` (`GET /api/clients/me/orders`) and can retry
   an unpaid PayFast order (`canPayNow`).

## 4. Merchant fulfils

Merchant admin → **Orders**: fulfilment moves **processing → ready → completed**
(`POST /api/m/<slug>/admin/orders/{id}/fulfillment`). Orders can be cancelled while unpaid.
Salon bookings move **pending → confirmed → in_progress → completed** (or **cancelled**).

## 5. Merchant pays the platform

1. After the trial ends, merchant admin → **Plan & billing** shows the plan and amount due.
2. `POST /api/m/<slug>/admin/subscription/payfast-checkout` → PayFast hosted page.
3. PayFast's ITN to `/api/payments/payfast/subscription/webhook` activates the paid period. Checkout is refused
   while the trial is still running (`trial_still_active`).

Plans: `STARTER`, `STANDARD`, `PREMIUM`; prices are set by support in the console.

## 6. Platform support

The support console (`/support`) lists merchants, subscriptions, orders, bookings and tickets, can sign in as a
merchant ("shadow") to help, toggles platform features, and keeps an audit log. What each support user can do is
controlled by their permissions; platform admins can do everything.

## 7. Session expiry and password reset

- A login lasts 12 hours (`APP_JWT_TTL_HOURS`). When an API call returns 401 the web app signs the user out once and
  returns them to the right sign-in page.
- **Forgot password** → `POST /api/auth/forgot-password` emails a link to `PUBLIC_APP_BASE_URL/reset-password?token=…`
  (valid `PASSWORD_RESET_TOKEN_TTL_HOURS`, default 24).
