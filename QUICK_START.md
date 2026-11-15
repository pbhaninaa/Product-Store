# Quick start — Product-Store

Local development for the API, web app, mobile shell and automated tests.

## Prerequisites

- **Java 17+** and **Maven 3.9+** — `java -version`, `mvn -v`
- **Node.js 18+** (CI uses 22) — for `frontend/` and `mobile/`
- **MySQL** (optional) — only for the `local` profile; SIT and tests use in-memory H2

## 1. Run the API (SIT profile)

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=sit
```

API: `http://localhost:8080` · health: `http://localhost:8080/api/health`.
SIT uses H2 in memory, so data resets on every restart and the demo store is re-seeded.

Against your own MySQL instead: copy `src/main/resources/application-local.example.properties` to
`application-local.properties` (gitignored), set the password, then run with `-Dspring-boot.run.profiles=local`.

## 2. Run the web app

```bash
cd frontend
copy .env.example .env      # macOS/Linux: cp .env.example .env
npm install
npm run serve
```

SPA: `http://localhost:8085`. `VUE_APP_API_BASE` in `.env` points at the API (see [docs/ENVIRONMENTS.md](docs/ENVIRONMENTS.md)).

## 3. Demo accounts (SIT)

| Who | Login | Password |
|-----|-------|----------|
| Merchant (store `demo`) | `demo@localhost` | `Demo@123456` |
| Shopper | `client@localhost` | `Client@123456` |
| Platform admin | `admin@localhost` | `Admin@123456` |

Storefront `http://localhost:8085/m/demo`, merchant admin `/m/demo/admin`. More in [docs/DEMO_DATA.md](docs/DEMO_DATA.md).

## 4. Automated tests

```bash
cd backend && mvn test                 # unit, integration and end-to-end API journeys
cd frontend && npm test                # Vitest
cd frontend && npm run test:e2e        # Cypress (needs steps 1 and 2 running)
cd mobile && npx jest                  # mobile
```

What each layer covers: [backend/TESTING.md](backend/TESTING.md). CI runs all of them on every push.

## 5. Mobile

```bash
cd mobile
npm install
npm run android:sit        # emulator loads http://10.0.2.2:8085
```

See [mobile/README.md](mobile/README.md).

## 6. Working on a change

```bash
git checkout SIT && git pull
git checkout -b feature/short-name
# …commit…
git push -u origin feature/short-name     # CI runs
git checkout SIT && git merge --no-ff feature/short-name -m "Merge feature/short-name into SIT"
git push origin SIT
```

Promotion to UAT/PROD: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).
