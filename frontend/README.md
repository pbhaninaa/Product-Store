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

## Tests

| Command | What |
|---------|------|
| `npm test` | Vitest unit, integration and regression suites (`tests/`) |
| `npx vue-cli-service lint --no-fix` | ESLint (as run in CI) |
| `npm run test:e2e` | Cypress store journey — needs the SIT backend on :8080 and `npm run serve` on :8085 |

See [../backend/TESTING.md](../backend/TESTING.md) for every test layer.

## Vercel

See **[../docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md)** and [../docs/ENVIRONMENTS.md](../docs/ENVIRONMENTS.md). Root Directory = `frontend`. Set `VUE_APP_API_BASE` per environment; redeploy after changes.
