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
- [ ] PayFast: `PAYFAST_*` set, `PAYFAST_SANDBOX=true` on UAT and `false` (or unset) on PROD, `PAYFAST_PUBLIC_BASE_URL` is the backend `https://` domain
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

### Payments: PayFast

PayFast handles customer orders, salon bookings and merchant subscriptions (card and Instant EFT).

- PayFast posts payment notifications (ITNs) to `PAYFAST_PUBLIC_BASE_URL/api/payments/payfast/webhook` and
  `/api/payments/payfast/subscription/webhook`. Each one must carry a valid signature built with `PAYFAST_PASSPHRASE`;
  the amount and merchant reference are checked, and the order row is locked so repeated or simultaneous
  notifications settle it only once.
- `GET /api/payments/payfast/configured` reports whether PayFast is usable and lists any missing variables.
- Peach Payments is legacy: no new checkouts. Its variables can stay unset.

### Managed in the app, not in environment variables

- Subscription plan prices: Support console → **Subscriptions**.
- Platform features (merchant sign-up, payments, PayFast): Support console → **Features**.
- Which payment methods a store accepts (cash, manual EFT, PayFast), banking details: Merchant admin → **Store**.
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
4. **Database:** Hibernate `ddl-auto=update` only **adds** tables/columns, so an older backend still starts on a
   newer schema. Startup migrations are idempotent, but data-moving ones cannot be undone by rolling back code —
   take a Railway backup before promoting such a release.
5. **Git:** revert the merge on `PROD` (`git revert -m 1 <merge-sha>`), push, and let CI + deploys run.

## 5. Health endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /api/health` | Railway healthcheck; app is up |
| `GET /actuator/health` (+ `/liveness`, `/readiness`) | Spring Actuator; readiness includes the database |
| `GET /api/payments/payfast/configured` | PayFast configuration status |

## 8. Post-deploy checks

Run on UAT after each promotion, then on PROD.

1. **Startup log:** no `CORS has no allowed origins` error; `PayFast is configured for sandbox` (UAT) or `live` (PROD);
   no `PayFast is enabled but unavailable`; no SQL bind values in the log.
2. **Health:** `/api/health` and `/actuator/health/readiness` return UP.
3. **CORS + login:** open the Vercel URL and sign in as a merchant. A blank page or CORS errors in the browser
   console mean `*_CORS_ORIGINS` is wrong.
4. **Storefront:** open `/m/<a-store-slug>`, product images load (they come from `PUBLIC_BASE_URL` + the volume).
5. **PayFast order (UAT sandbox):** place a PayFast order, pay with a sandbox card, land on the return page, and
   see the order **paid** in Merchant admin → Orders with stock reduced once.
6. **Subscription:** on a merchant whose trial has ended, pay in **Plan & billing**; the plan shows active.
7. **Password reset:** request a reset; the email link opens the Vercel site (proves `PUBLIC_APP_BASE_URL`).
8. **Support console:** platform admin can sign in at `/m/platform/admin?support=1`.
9. **No demo data** on UAT/PROD (no `demo` store, no `demo@localhost`).
