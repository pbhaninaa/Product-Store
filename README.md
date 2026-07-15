# Product-Store

Multi-tenant storefront + merchant admin + support console.

| Layer | Stack | Deploy |
|-------|--------|--------|
| Frontend | Vue 2 + Vue CLI + Vuetify | **Vercel** (`frontend/`) |
| Backend | Spring Boot 3 / Java 17 | **Railway** (`backend/`) |
| Database | MySQL (UAT/PROD) · H2 (SIT) | Railway / in-memory |

## Branches (MarketPlace-style)

| Branch | Profile | Use |
|--------|---------|-----|
| `SIT` | `sit` | Local / integration (H2) |
| `UAT` | `uat` | Staging |
| `PROD` | `prod` | Production |

- **[ENV.md](ENV.md)** — variables  
- **[DEPLOYMENT.md](DEPLOYMENT.md)** — Railway + Vercel  
- **[railway-env-variables.example.txt](railway-env-variables.example.txt)** — paste templates  

## Run locally

```bash
# Backend — SIT (H2) or local MySQL
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=sit
# mvn spring-boot:run -Dspring-boot.run.profiles=local

# Frontend
cd frontend
copy .env.example .env
npm install
npm run serve
```

| What | URL |
|------|-----|
| API | `http://localhost:8080` |
| SPA | `http://localhost:8085` |

Set **`VUE_APP_API_BASE`** in `frontend/.env` (Vue CLI: `VUE_APP_*`, not `VITE_*`).

## Deploy (short)

1. Push to **`UAT`** or **`PROD`**.
2. Railway: root `backend/`, matching branch, MySQL + env vars for that env.
3. Vercel: root `frontend/`, matching branch, `VUE_APP_API_BASE` → that backend URL.
4. Set Railway CORS / `PUBLIC_APP_BASE_URL` to the Vercel URL.

Health: `GET /actuator/health` and `GET /api/health`.
