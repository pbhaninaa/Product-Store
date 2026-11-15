# Product-Store

Multi-tenant storefront (shops and salons) + merchant admin + platform support console.

| Layer | Stack | Path | Deploy |
|-------|-------|------|--------|
| Web | Vue 2 + Vue CLI + Vuetify | `frontend/` | **Vercel** |
| API | Spring Boot 3 / Java 17 | `backend/` | **Railway** (Docker) |
| Database | MySQL (UAT/PROD) · H2 (SIT, tests) | — | Railway |
| Mobile | React Native WebView shell | `mobile/` | Play Store / App Store |
| Payments | PayFast (card, Instant EFT) · cash · manual EFT | — | — |

## Branches

| Branch | Profile | Use |
|--------|---------|-----|
| `feature/*` | — | One branch per change, merged into `SIT` |
| `SIT` | `sit` | Integration (H2 + demo seed); CI on every push |
| `UAT` | `uat` | Staging |
| `PROD` | `prod` | Production |

## Start here

- **[QUICK_START.md](QUICK_START.md)** — run everything locally, demo logins, tests
- **[docs/README.md](docs/README.md)** — docs index (deployment, environments, user journey, architecture, demo data)
- **[DEPLOYMENT.md](DEPLOYMENT.md)** — deploy summary · full guide [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)
- **[backend/ENV.md](backend/ENV.md)** — every backend variable · [backend/TESTING.md](backend/TESTING.md) — test layers
- **[USER_MANUALS/INDEX.md](USER_MANUALS/INDEX.md)** — manuals for shoppers, merchants and support
- **[MOBILE.md](MOBILE.md)** — mobile app

## Run locally (short)

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=sit     # http://localhost:8080
cd frontend && npm install && npm run serve                          # http://localhost:8085
```

Health: `GET /api/health` and `GET /actuator/health`.

CI: `.github/workflows/ci.yml` — backend tests, frontend lint/tests/build, Cypress journey on a SIT stack, mobile type-check/tests.
