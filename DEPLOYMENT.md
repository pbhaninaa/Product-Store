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
- [ ] `APP_JWT_SECRET`, CORS origins, `PUBLIC_BASE_URL`, `PUBLIC_APP_BASE_URL`, SendGrid set
- [ ] Durable `UPLOADS_DIR` volume on Railway (product images + private subscription proofs sibling folder)
- [ ] Platform banking set (Support console or `PLATFORM_BANK_*` env) before merchants pay
- [ ] Demo bootstrap **off** on UAT/PROD (`app.bootstrap.demoMerchant.enabled=false`)
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

1. Open Support console → **Subscriptions**.
2. Set real **platform banking** (account merchants EFT into). Placeholder `0000000000` is rejected as “not configured”.
3. Optional: seed via Railway env `PLATFORM_BANK_NAME`, `PLATFORM_BANK_ACCOUNT_NAME`, `PLATFORM_BANK_ACCOUNT_NUMBER`, `PLATFORM_BANK_BRANCH_CODE`, `PLATFORM_BANK_PAYMENT_LINK`.
4. Mount `UPLOADS_DIR` on a volume. Payment proof PDFs are stored under `{UPLOADS_DIR}/../private/subscription-proofs/` and are **not** served on public `/uploads/**`.
5. Review pending proofs in Support → Subscriptions. Only **Platform Admin** may force-activate without proof.

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
