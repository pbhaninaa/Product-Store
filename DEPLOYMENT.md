# Deployment Guide — Product-Store

**Last updated:** November 2026

Full deployment documentation lives in **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**.

## Quick summary

| Component | Path | Hosting |
|-----------|------|---------|
| API | `backend` | Railway + MySQL |
| Web | `frontend` | Vercel |
| Mobile | `mobile` | Play Store / App Store (WebView shell) |

Branches: **`SIT`** → **`UAT`** → **`PROD`**. Merge `feature/*` branches into `SIT`, wait for CI, then merge
`SIT` into `UAT` and `UAT` into `PROD`.

Key env vars:

- **Railway:** `SPRING_PROFILES_ACTIVE`, datasource, `APP_JWT_SECRET`, `PUBLIC_APP_BASE_URL` (https, required),
  `UAT_CORS_ORIGINS` / `PROD_CORS_ORIGINS`, `PUBLIC_BASE_URL`, `UPLOADS_DIR`, `PLATFORM_ADMIN_EMAIL` /
  `PLATFORM_ADMIN_PASSWORD`, SendGrid, optional Twilio WhatsApp
- **Payments:** `PAYFAST_ENABLED`, `PAYFAST_SANDBOX`, `PAYFAST_MERCHANT_ID`, `PAYFAST_MERCHANT_KEY`,
  `PAYFAST_PASSPHRASE`, `PAYFAST_PUBLIC_BASE_URL` (Peach is legacy)
- **Vercel:** `VUE_APP_API_BASE` (backend origin, no `/api`), optional `VUE_APP_SITE_NAME`

Plan prices, platform features and per-store payment options are managed in the app, not in env vars.
After each deploy, run the [post-deploy checks](docs/DEPLOYMENT.md#8-post-deploy-checks).

Also see [docs/ENVIRONMENTS.md](docs/ENVIRONMENTS.md), [backend/ENV.md](backend/ENV.md), and [README.md](README.md).
