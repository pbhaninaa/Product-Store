# Environments: SIT, UAT, PROD

**Last updated:** November 2026

## Git branches ↔ deployments

| Git branch | Typical use |
|------------|-------------|
| **`SIT`** | Integration branch on `origin`. Merge `feature/*` branches here; run locally with Spring profile **`sit`**. CI runs on every push |
| **`UAT`** | Build/deploy from this branch for staging (Railway UAT + Vercel UAT) |
| **`PROD`** | Build/deploy from this branch for production only |

Promotion: `feature/*` → `SIT` → `UAT` → `PROD` (see [DEPLOYMENT.md](DEPLOYMENT.md#source-control-sit-uat-prod)).

---

| Environment | Who uses it | Frontend (`frontend/`) | Backend (`backend/`) | Database |
|-------------|-------------|------------------------|----------------------|----------|
| **SIT** | Developers, CI | `npm run serve` → `http://localhost:8085` | `mvn spring-boot:run -Dspring-boot.run.profiles=sit` → `http://localhost:8080` | H2 in memory, recreated each start, demo seed |
| **UAT** | Testers | Vercel, `npm run build:uat`, `VUE_APP_API_BASE` = Railway UAT backend | Railway, `SPRING_PROFILES_ACTIVE=uat` | Railway MySQL (UAT) |
| **PROD** | Live | Vercel, `npm run build:prod`, `VUE_APP_API_BASE` = Railway PROD backend | Railway, `SPRING_PROFILES_ACTIVE=prod` | Railway MySQL (PROD) |

A `local` profile also exists for running against your own MySQL: copy
`backend/src/main/resources/application-local.example.properties` to `application-local.properties` (gitignored).

## Web app — Vue CLI (`frontend/`)

Vue CLI only exposes variables prefixed **`VUE_APP_`** and bakes them in **at build time**: redeploy after changing them.

| Variable | Value |
|----------|-------|
| `VUE_APP_API_BASE` | Backend **origin only**, e.g. `https://backend-uat.up.railway.app` — no `/api`, no trailing slash (a trailing `/api` is stripped) |
| `VUE_APP_SITE_NAME` | Optional display name, e.g. `Product Store (UAT)` |

- **SIT:** `frontend/.env` (copy `.env.example`) → `VUE_APP_API_BASE=http://localhost:8080`.
- **UAT:** Vercel env vars for the UAT deployment → Railway UAT backend origin.
- **PROD:** Vercel env vars for Production → Railway PROD backend origin.

Vercel runs `node scripts/vercel-build.js`, which picks `build:sit` / `build:uat` / `build:prod` from the
branch name (`SIT`, `UAT`, `PROD`; exact case). **If `VUE_APP_API_BASE` is unset it falls back to the PROD
Railway API**, so always set it explicitly for UAT.

The mobile app injects its own API origin at runtime (`window.__productStoreApiBaseUrl`), which takes priority
over the build-time value.

## Backend — Spring Boot (`backend/`)

- **SIT:** no env vars needed. H2, CORS open, demo merchant/client seeded, dev-only admin `admin@localhost` / `Admin@123456`.
- **UAT / PROD:** set at least `SPRING_PROFILES_ACTIVE`, datasource, `APP_JWT_SECRET`, `PUBLIC_APP_BASE_URL`
  (https), `UAT_CORS_ORIGINS` / `PROD_CORS_ORIGINS`, `PUBLIC_BASE_URL`, `UPLOADS_DIR` (+ volume), and Paystack.
  UAT/PROD **refuse to start** without an https `PUBLIC_APP_BASE_URL` or `APP_JWT_SECRET`.

Full variable list: [../backend/ENV.md](../backend/ENV.md) and
[../backend/railway-env-values.example.txt](../backend/railway-env-values.example.txt).

## Mobile (`mobile/`)

Android flavors `sit` / `uat` / `prod` (`assembleUatRelease` etc.). SIT loads `http://10.0.2.2:8085` (emulator →
your PC). UAT and PROD load the hosted web app set in `mobile/src/config/environments.ts`
(`HOSTED_WEB_APP_URL`, `HOSTED_API_BASE_URL`). See [../mobile/README.md](../mobile/README.md).
