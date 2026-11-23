# Deployment Guide — Product-Store

**Last updated:** November 2026

Vue 2 SPA (**Vercel**, `frontend/`) + Spring Boot API (**Railway**, `backend/`) + MySQL (**Railway**).

## Source control: SIT, UAT, PROD

| Branch | Spring profile | Deploys to |
|--------|----------------|------------|
| `SIT` | `sit` | Nothing — local H2 + CI |
| `UAT` | `uat` | Railway UAT backend + Vercel UAT |
| `PROD` | `prod` | Railway PROD backend + Vercel Production |

### Release flow

1. Branch from `SIT`: `git checkout -b feature/short-name`.
2. Commit, push the feature branch (CI runs), then merge into `SIT`:
   `git checkout SIT && git merge --no-ff feature/short-name -m "Merge feature/short-name into SIT"`.
3. Push `SIT`; wait for CI to go green (backend, frontend, browser journey, mobile).
4. Promote: `git checkout UAT && git merge SIT && git push origin UAT`. Run the [post-deploy checks](#8-post-deploy-checks) on UAT.
5. Promote: `git checkout PROD && git merge UAT && git push origin PROD`. Run the post-deploy checks on PROD.

Production changes only land on `PROD` through `UAT`.

## Pre-deployment checklist

- [ ] CI green on the commit being promoted
- [ ] Railway variables for that environment match [../backend/railway-env-values.example.txt](../backend/railway-env-values.example.txt)
- [ ] `PUBLIC_APP_BASE_URL` is the Vercel URL (`https://`) — UAT/PROD will not start without it
- [ ] `UAT_CORS_ORIGINS` / `PROD_CORS_ORIGINS` list the Vercel URL(s) — browsers are refused otherwise
- [ ] `APP_JWT_SECRET` set (different per environment)
- [ ] Paystack: `PAYSTACK_ENABLED=true` and `PAYSTACK_SECRET_KEY` set (`sk_test_...` on UAT, `sk_live_...` on PROD); the webhook URL `<backend https domain>/api/payments/paystack/webhook` is registered in the Paystack dashboard
- [ ] First deploy of the Paystack release: the one-time `LegacyPaymentSchemaMigration` has been run against a copy of the UAT database first, and its INFO log lines checked (see Payments: Paystack below)
- [ ] Railway volume mounted at `/app/data` and `UPLOADS_DIR=/app/data/uploads`
- [ ] New database? `PLATFORM_ADMIN_EMAIL` + `PLATFORM_ADMIN_PASSWORD` set (no defaults on UAT/PROD)
- [ ] Vercel `VUE_APP_API_BASE` = that environment's Railway backend origin (no `/api`)
- [ ] Release adds a data-moving startup migration? Take a Railway MySQL backup first

---

# What to set where

## 1. Railway (backend — `backend/`)

1. Railway project → add **MySQL** (separate per environment).
2. Add a service from this repo → **Root Directory `backend`** → branch `UAT` or `PROD`.
3. Railway reads `backend/railway.toml` (Dockerfile build, healthcheck `/api/health`, 600 s timeout). If the
   service **Settings** UI still shows `/actuator/health`, change it to `/api/health` — the UI overrides the file.
4. Add a **volume** mounted at `/app/data`.
5. Paste the variables for that branch from [../backend/railway-env-values.example.txt](../backend/railway-env-values.example.txt).
6. Deploy, then confirm `https://YOUR-BACKEND/api/health` → `{"ok":true,…}`.

Every variable, whether it is needed and where its value comes from: [../backend/ENV.md](../backend/ENV.md).

### Payments: Paystack

Paystack is the only online payment provider. It handles customer orders, salon bookings and merchant subscriptions
(card and Instant EFT), all paid into the single platform Paystack account. Split payments to providers are not built yet.

- Set `PAYSTACK_ENABLED`, `PAYSTACK_SECRET_KEY` (`sk_test_...` on SIT/UAT, `sk_live_...` on PROD) and optionally
  `PAYSTACK_BASE_URL` (default `https://api.paystack.co`). See [../backend/ENV.md](../backend/ENV.md).
- Register the webhook URL `<backend https domain>/api/payments/paystack/webhook` in the Paystack dashboard. Paystack signs each
  call with the `x-paystack-signature` header (HMAC SHA512 of the raw body, using the secret key); calls with a bad signature
  are rejected. The order or booking row is locked, so repeated or simultaneous notifications settle it only once.
- If the webhook is late, the return page's payment-status check asks Paystack to verify the transaction and confirms it.
- `GET /api/payments/paystack/configured` reports whether Paystack is usable.
- Customers return to `/m/<slug>/payment/return` (subscriptions: `/payment/return`).

#### One-time database migration (first deploy of this release)

On startup, before Hibernate runs, `LegacyPaymentSchemaMigration` renames the previous payment provider's columns, table, indexes
and stored values to the provider neutral `online_*` names, keeping all data.

1. Run it first against a copy of the UAT database and check its INFO log lines (for example
   `Legacy payment schema migration finished: N change(s)`, or `nothing to do` when already migrated).
2. Then deploy to UAT and PROD. It is safe to run again; the class is deleted in a follow-up release once SIT, UAT and PROD
   have all run it.

#### Cut-over note

Payments started with the previous payment provider and still unpaid at deploy time will not confirm automatically.
Deploy at a quiet time, check for pending online orders and bookings first, and reconcile any by hand.

### Paystack release: deploy and rollback

This release is not a normal deploy, because the one-time `LegacyPaymentSchemaMigration` renames payment columns and a table.

**Before UAT**

- Run the new backend once against a copy of the UAT database and check the migration's INFO log lines
  (`Legacy payment schema migration finished: N change(s)`).
- Confirm MySQL is 8.0.16 or newer (`SELECT VERSION()`).

**Before UAT and PROD**

- A database backup before deploying is mandatory.
- In the Paystack dashboard, set the account so the merchant (the platform) bears the fees, or payments will not settle.
- Register the webhook URL `<backend https domain>/api/payments/paystack/webhook`. Paystack allows one webhook URL per account and
  mode (test or live).

**Deploying**

- Deploy in a quiet or maintenance window with no overlap between old and new instances. Railway keeps the old instance serving
  until the new one is healthy, but after the rename the old instance's queries on orders, bookings and subscriptions fail.
  Scale down or stop the old instance first, or accept a short outage.
- If the new instance fails its healthcheck after the migration has run, the old instance is already broken against the renamed
  schema. Restore the backup or fix forward.

**Rolling back this release**

- Restore the database backup **and** redeploy the previous image.
- Redeploying the previous image alone breaks the app: it recreates empty old columns, turns online payment off for every shop,
  and cannot read the migrated rows.

### Managed in the app, not in environment variables

- Subscription plan prices: Support console → **Subscriptions**.
- Platform features (merchant sign-up, payments, Paystack): Support console → **Features**.
- Which payment methods a store accepts (cash, manual EFT, online payment), banking details: Merchant admin → **Store**.
- The 7-day free trial starts at store creation and cannot be reset.

## 2. Vercel (frontend — `frontend/`)

1. Import the repo → **Root Directory `frontend`**.
2. Production branch **`PROD`**; UAT as a separate project (or Preview) on branch **`UAT`**.
3. Build is driven by `frontend/vercel.json` → `node scripts/vercel-build.js` (picks `build:uat` / `build:prod` from the branch). Output `dist`.
4. Environment variables:

| Variable | Value |
|----------|-------|
| `VUE_APP_API_BASE` | That environment's Railway backend origin (no `/api`). **Set it for UAT** — unset falls back to the PROD API |
| `VUE_APP_SITE_NAME` | Optional |

5. Redeploy after changing variables (they are baked in at build time).

## 3. Mobile

See [../mobile/README.md](../mobile/README.md). Release APK/AAB builds need `mobile/android/keystore.properties`
(copy the `.example`); they refuse to build with the debug key.

## 4. Rollback plan

1. **Vercel:** Deployments → last good deployment → **Redeploy** / **Promote**. Check `VUE_APP_API_BASE` is unchanged.
2. **Railway:** service → Deployments → redeploy the last successful deployment. If a new deploy fails its
   healthcheck, Railway keeps the previous one running — read the deploy logs (a missing `PUBLIC_APP_BASE_URL`
   or `APP_JWT_SECRET` stops startup with a clear message).
3. **Bad variables:** fix the variable, then redeploy the service; no code change needed.
4. **Database:** Hibernate `ddl-auto=update` only **adds** tables/columns, so an older backend normally still starts on a
   newer schema. Startup migrations are idempotent, but data-moving ones cannot be undone by rolling back code, so take
   a database backup before promoting such a release. **The Paystack release is an exception:** its one-time migration
   renames columns and a table, so an older backend does not work on the migrated schema. See
   [Paystack release: deploy and rollback](#paystack-release-deploy-and-rollback).
5. **Git:** revert the merge on `PROD` (`git revert -m 1 <merge-sha>`), push, and let CI + deploys run.

## 5. Health endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /api/health` | Railway healthcheck; app is up |
| `GET /actuator/health` (+ `/liveness`, `/readiness`) | Spring Actuator; readiness includes the database |
| `GET /api/payments/paystack/configured` | Paystack configuration status |

## 8. Post-deploy checks

Run on UAT after each promotion, then on PROD.

1. **Startup log:** no `CORS has no allowed origins` error; no `Paystack is enabled in PROD with a test secret key` warning on PROD;
   no Paystack startup errors; the `Legacy payment schema migration` line is `finished` or `nothing to do`; no SQL bind values in the log.
2. **Health:** `/api/health` and `/actuator/health/readiness` return UP.
3. **CORS + login:** open the Vercel URL and sign in as a merchant. A blank page or CORS errors in the browser
   console mean `*_CORS_ORIGINS` is wrong.
4. **Storefront:** open `/m/<a-store-slug>`, product images load (they come from `PUBLIC_BASE_URL` + the volume).
5. **Paystack order (UAT, test key):** place an online order, pay with a Paystack test card, land on the return page, and
   see the order **paid** in Merchant admin → Orders with stock reduced once.
6. **Subscription:** on a merchant whose trial has ended, pay in **Plan & billing**; the plan shows active.
7. **Password reset:** request a reset; the email link opens the Vercel site (proves `PUBLIC_APP_BASE_URL`).
8. **Support console:** platform admin can sign in at `/m/platform/admin?support=1`.
9. **No demo data** on UAT/PROD (no `demo` store, no `demo@localhost`).
