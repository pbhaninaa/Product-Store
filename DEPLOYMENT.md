# Deployment Guide — Product-Store

**Last updated:** July 2026

Vue 2 SPA (**Vercel**) + Spring Boot API + MySQL (**Railway**).  
Env layout matches **MarketPlace** (SIT / UAT / PROD branches).

Full variable reference: **[ENV.md](ENV.md)** · paste templates: **[railway-env-variables.example.txt](railway-env-variables.example.txt)**

## Branches

| Branch | Spring profile | Purpose |
|--------|----------------|---------|
| `SIT` | `sit` | Local / integration (H2 in-memory) |
| `UAT` | `uat` | Staging (MySQL on Railway) |
| `PROD` | `prod` | Production (MySQL on Railway) |

Deploy **from the matching branch**. Production changes must land on `PROD`.

| Piece | Where | Notes |
|-------|--------|--------|
| MySQL | Railway addon | Linked to UAT/PROD backend |
| Backend | Railway, root `backend/` | Dockerfile + `railway.toml`; branch `UAT` or `PROD` |
| Frontend | Vercel, root `frontend/` | Build `npm run build:uat` / `build:prod`; `VUE_APP_*` at build time |

## Pre-deployment checklist

- [ ] Code on the correct branch (`SIT` / `UAT` / `PROD`)
- [ ] Railway MySQL + backend service (UAT/PROD)
- [ ] `SPRING_PROFILES_ACTIVE` = `uat` or `prod`
- [ ] `APP_JWT_SECRET`, CORS origins, `PUBLIC_BASE_URL`, `PUBLIC_APP_BASE_URL`, `SENDGRID_API_KEY`, and `EMAIL_DOMAIN` set
- [ ] Durable `UPLOADS_DIR` volume on Railway (product images; legacy private subscription proofs sibling folder is read-only history)
- [ ] Platform Peach credentials (`PEACH_*`) set so merchants can pay subscriptions online
- [ ] Demo merchant/client seed **SIT only** (`app.bootstrap.demoMerchant.enabled` / `demoClient.enabled` = `false` on local, UAT, PROD)
- [ ] Vercel `VUE_APP_API_BASE` = that environment’s Railway backend origin (no `/api`)
- [ ] CORS origins match that environment’s frontend URL (no trailing slash)

---

## 1) Railway — MySQL + backend

1. Create a Railway project → add **MySQL** (separate projects or DBs for UAT vs PROD).
2. Add a service from this repo → **Root Directory = `backend`**.
3. **Branch:** `PROD` (or `UAT`).
4. Railway reads `backend/railway.toml` (Dockerfile, healthcheck `/actuator/health`).
5. Variables → section for that branch in `railway-env-variables.example.txt`.
6. Set `PUBLIC_BASE_URL` to the backend public domain.
7. Confirm: `https://YOUR-BACKEND/actuator/health` → `{"status":"UP"}`.

### Database tip

`SPRING_DATASOURCE_URL=jdbc:${{MySQL.MYSQL_URL}}` (Railway reference).  
Must resolve to `jdbc:mysql://...`. Or leave `SPRING_DATASOURCE_*` empty and use linked `MYSQLHOST` / `MYSQLUSER` / …

---

## 2) Vercel — frontend

1. Import the same GitHub repo.
2. **Root Directory = `frontend`**.
3. Point Production deploy to branch **`PROD`**, Preview/staging to **`UAT`** (or separate Vercel projects per env).
4. Build commands:
   - PROD: `npm run build:prod` (or `npm run build`)
   - UAT: `npm run build:uat`
   - SIT local: `npm run build:sit`
5. Output: `dist` · Install: `npm install`.
6. Env vars (per environment):

| Variable | Value |
|----------|--------|
| `VUE_APP_API_BASE` | That env’s Railway backend URL |
| `VUE_APP_SITE_NAME` | optional |

7. Redeploy after changing env vars (baked at build time).

---

## 3) Wire CORS + frontend URL on Railway

```text
# PROD
PUBLIC_APP_BASE_URL=https://your-prod-frontend.vercel.app
PROD_CORS_ORIGINS=https://your-prod-frontend.vercel.app

# UAT
PUBLIC_APP_BASE_URL=https://your-uat-frontend.vercel.app
UAT_CORS_ORIGINS=https://your-uat-frontend.vercel.app
```

Restart backend after fixing CORS.

---

## 3b) Subscriptions (billing go-live)

1. Configure platform Peach (`PEACH_ENABLED=true` plus client/merchant/entity/secret token).
2. **New merchants** automatically receive a one-time **7-day Free Trial** from store creation (UTC `trial_start_at` / `trial_end_at`). Full Premium entitlement — no plan choice or payment required during the trial. Trial dates are durable and never reset; support cannot reissue or force-activate trials.
3. After trial expiry, merchants choose a plan in **Plan & billing** and pay with Peach Hosted Checkout (**Card** or **Instant EFT** → Peach `PAYBYBANK`). Cash / manual EFT for subscriptions is disabled (410).
4. Only a **verified, idempotent Peach callback** (webhook or signed shopper return) activates or renews a paid period.
5. Existing merchants missing trial columns are **backfilled once** from `tenant.created_at` (historical window — not a fresh trial). Paid active Peach periods are left unchanged.
6. Product / salon **customer** checkout offers **Cash**, **Manual EFT** (proof upload), and/or **Peach** (shop settings: accept cash / customer EFT / Peach). Under Peach, customers choose **Card** or **Instant EFT** (`PAYBYBANK`).
7. Legacy **subscription** EFT proof upload, support approve/reject, force-activate, and platform-banking **updates** return **410 Gone**. Support can still list/open historical proof PDFs (read-only).

Trial columns are applied automatically by Hibernate `ddl-auto=update` plus idempotent `PeachSchemaMigration` (`V14__merchant_trial_dates.sql` is reference-only — **no manual SQL**).

---

## Local / SIT

```bash
# Backend (H2)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=sit

# Or MySQL local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Frontend
cd frontend
copy .env.example .env
npm install
npm run serve
```

API: `http://localhost:8080` · SPA: `http://localhost:8085`.

---

## Promote

```bash
git checkout UAT
git merge SIT
git push origin UAT

git checkout PROD
git merge UAT
git push origin PROD
```

## Rollback

- **Vercel:** redeploy a previous deployment.
- **Railway:** redeploy a previous successful deploy.

## Diff vs MarketPlace

| MarketPlace | Product-Store |
|-------------|---------------|
| `VITE_API_BASE` | `VUE_APP_API_BASE` (Vue CLI) |
| Branches `SIT` / `UAT` / `PROD` | Same |
| `PUBLIC_APP_BASE_URL` = UI | Same; plus **`PUBLIC_BASE_URL`** = API (uploads) |
| Health `/actuator/health` | Same |
| `APP_JWT_SECRET`, CORS, `SPRING_DATASOURCE_*` / `MYSQL*` | Same |
