# Environment variables � Product-Store

Same layout as **MarketPlace**: branches **SIT** / **UAT** / **PROD**, Railway (MySQL + API) + Vercel (SPA).

| Branch | `SPRING_PROFILES_ACTIVE` | DB |
|--------|--------------------------|-----|
| `SIT` | `sit` | H2 (local/CI) |
| `UAT` | `uat` | MySQL |
| `PROD` | `prod` | MySQL |

Never rely on the default `local` profile on Railway.

Frontend uses **Vue CLI** / **`VUE_APP_*`** (not MarketPlace's `VITE_*`).

Paste-ready templates: **[railway-env-variables.example.txt](railway-env-variables.example.txt)**

---

## Railway layout

| Service | Root directory | Branch |
|---------|----------------|--------|
| MySQL | Railway addon | � |
| Backend API | `backend/` | `UAT` or `PROD` |
| Frontend | Vercel (`frontend/`) | `UAT` or `PROD` |

Set **`VUE_APP_API_BASE`** to the backend **origin only** (no `/api`, no trailing slash).

---

## Backend � shared names (MarketPlace-compatible)

| Variable | Notes |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `uat` or `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://...` or `jdbc:${{MySQL.MYSQL_URL}}` |
| `SPRING_DATASOURCE_USERNAME` | `${{MySQL.MYSQLUSER}}` |
| `SPRING_DATASOURCE_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `APP_JWT_SECRET` | Different secret per env (32+ chars) |
| `PUBLIC_BASE_URL` | **Backend** public URL (uploads) |
| `PUBLIC_APP_BASE_URL` | **Frontend** URL (used in password-reset email links) |
| `UAT_CORS_ORIGINS` / `PROD_CORS_ORIGINS` | Match frontend URL |
| `SENDGRID_API_KEY` | Can share SendGrid across envs |
| `EMAIL_DOMAIN` | Domain used for `info@`, `support@`, `security@`, `billing@`, and `no-reply@` senders |
| `EMAIL_FROM` | Legacy sender fallback when `EMAIL_DOMAIN` is blank |
| `WHATSAPP_ENABLED` + Twilio | Optional |
| `UPLOADS_DIR` | Durable volume for public product images |
| `PLATFORM_BANK_NAME` / `PLATFORM_BANK_ACCOUNT_NAME` / `PLATFORM_BANK_ACCOUNT_NUMBER` / `PLATFORM_BANK_BRANCH_CODE` | Optional first-boot seed for subscription EFT (else Support ? Subscriptions) |
| `PLATFORM_BANK_PAYMENT_LINK` | Optional |
| `PEACH_ENABLED` | `true` to turn on Peach Hosted Checkout V2 (product checkout, salon booking, subscription billing). App starts fine when `false` / unset. |
| `PEACH_SANDBOX` | `true` for the Peach test environment, `false` for live |
| `PEACH_CLIENT_ID` / `PEACH_CLIENT_SECRET` / `PEACH_MERCHANT_ID` / `PEACH_ENTITY_ID` | Platform Peach account credentials (OAuth2 client-credentials + entity) |
| `PEACH_SECRET_TOKEN` (a.k.a. `PEACH_WEBHOOK_SECRET`) | Webhook/result signature secret — verifies `POST /api/public/peach/webhook` and shopper return |

Peach schema (`peach_payment_method`, checkout ids, `accept_customer_peach`) is applied automatically at startup by Hibernate `ddl-auto=update` plus idempotent `PeachSchemaMigration`. **No manual SQL.** Flyway stays off.

Fallback if `SPRING_DATASOURCE_*` unset: `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`.

---

## Frontend � Vercel

| Variable | Example |
|----------|---------|
| `VUE_APP_API_BASE` | Backend origin for **that** branch's env |
| `VUE_APP_SITE_NAME` | Optional |

Build: `npm run build:sit` � `build:uat` � `build:prod`.

Redeploy after changing `VUE_APP_*`.

---

## First deploy checks

1. `GET /actuator/health` ? `{"status":"UP"}`
2. `GET /api/health` ? `{"ok":true,...}`
3. Open the matching Vercel URL and hit the API.
4. **Subscriptions:** Support ? Subscriptions ? set real platform banking (or `PLATFORM_BANK_*` env). Mount a durable `UPLOADS_DIR` volume � proof PDFs live next to it under `../private/subscription-proofs/` (not public `/uploads`).
5. Confirm `app.bootstrap.demoMerchant.enabled=false` on UAT/PROD.

See [DEPLOYMENT.md](DEPLOYMENT.md).
