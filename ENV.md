# Environment variables — Product-Store

Same layout as **MarketPlace**: branches **SIT** / **UAT** / **PROD**, Railway (MySQL + API) + Vercel (SPA).

| Branch | `SPRING_PROFILES_ACTIVE` | DB |
|--------|--------------------------|-----|
| `SIT` | `sit` | H2 (local/CI) |
| `UAT` | `uat` | MySQL |
| `PROD` | `prod` | MySQL |

Never rely on the default `local` profile on Railway.

Frontend uses **Vue CLI** ? **`VUE_APP_*`** (not MarketPlace’s `VITE_*`).

Paste-ready templates: **[railway-env-variables.example.txt](railway-env-variables.example.txt)**

---

## Railway layout

| Service | Root directory | Branch |
|---------|----------------|--------|
| MySQL | Railway addon | — |
| Backend API | `backend/` | `UAT` or `PROD` |
| Frontend | Vercel (`frontend/`) | `UAT` or `PROD` |

Set **`VUE_APP_API_BASE`** to the backend **origin only** (no `/api`, no trailing slash).

---

## Backend — shared names (MarketPlace-compatible)

| Variable | Notes |
|----------|--------|
| `SPRING_PROFILES_ACTIVE` | `uat` or `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://...` or `jdbc:${{MySQL.MYSQL_URL}}` |
| `SPRING_DATASOURCE_USERNAME` | `${{MySQL.MYSQLUSER}}` |
| `SPRING_DATASOURCE_PASSWORD` | `${{MySQL.MYSQLPASSWORD}}` |
| `APP_JWT_SECRET` | Different secret per env (32+ chars) |
| `PUBLIC_BASE_URL` | **Backend** public URL (uploads) |
| `PUBLIC_APP_BASE_URL` | **Frontend** URL |
| `UAT_CORS_ORIGINS` / `PROD_CORS_ORIGINS` | Match frontend URL |
| `SENDGRID_API_KEY` / `EMAIL_FROM` | Can share SendGrid across envs |
| `WHATSAPP_ENABLED` + Twilio | Optional |

Fallback if `SPRING_DATASOURCE_*` unset: `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD`.

---

## Frontend — Vercel

| Variable | Example |
|----------|---------|
| `VUE_APP_API_BASE` | Backend origin for **that** branch’s env |
| `VUE_APP_SITE_NAME` | Optional |

Build: `npm run build:sit` · `build:uat` · `build:prod`.

Redeploy after changing `VUE_APP_*`.

---

## First deploy checks

1. `GET /actuator/health` ? `{"status":"UP"}`
2. `GET /api/health` ? `{"ok":true,...}`
3. Open the matching Vercel URL and hit the API.

See [DEPLOYMENT.md](DEPLOYMENT.md).
