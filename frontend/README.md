# Product-Store frontend

Vue 2 + Vuetify SPA. API via **`VUE_APP_API_BASE`**.

## Run locally

```bash
npm install
copy .env.example .env
npm run serve
```

| Var | Notes |
|-----|--------|
| `VUE_APP_API_BASE` | Backend origin (local: `http://localhost:8080`) |
| `VUE_APP_SITE_NAME` | Optional |

SPA: **http://localhost:8085**. Restart after `.env` changes.

## Builds (branch-aligned)

| Script | Mode | Branch |
|--------|------|--------|
| `npm run build:sit` | sit | `SIT` |
| `npm run build:uat` | uat | `UAT` |
| `npm run build:prod` | production | `PROD` |

## Vercel

See **[../DEPLOYMENT.md](../DEPLOYMENT.md)**. Root Directory = `frontend`. Set `VUE_APP_API_BASE` per environment; redeploy after changes.
