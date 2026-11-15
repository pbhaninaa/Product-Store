# Demo data (SIT)

**Last updated:** November 2026

Base URL (local backend): `http://localhost:8080` · web app: `http://localhost:8085`

## Auto-seeded on the `sit` profile

The SIT profile uses an **in-memory H2 database**, so everything below is recreated on every backend start.
Seeding is hard-coded **off** on `local`, `uat` and `prod`.

| Account | Login | Password (default) | Override with |
|---------|-------|--------------------|---------------|
| Platform admin / support console | `admin@localhost` | `Admin@123456` | `PLATFORM_ADMIN_EMAIL`, `PLATFORM_ADMIN_PASSWORD` |
| Demo merchant owner (store `demo`) | `demo@localhost` | `Demo@123456` | `DEMO_MERCHANT_EMAIL`, `DEMO_MERCHANT_PASSWORD` |
| Demo client (shopper) | `client@localhost` | `Client@123456` | `DEMO_CLIENT_EMAIL`, `DEMO_CLIENT_PASSWORD`, `DEMO_CLIENT_NAME` |

The demo store:

- slug **`demo`**, name **Demo Store**, shop & salon, **STANDARD** plan active
- product **Demo candle** — R120, stock 12
- salon service **Demo blow-dry** — R250

## Where to go

| Who | URL |
|-----|-----|
| Shopper storefront | `http://localhost:8085/m/demo` |
| Shopper sign in / history | `/account/login`, `/account/history` |
| Merchant admin | `http://localhost:8085/m/demo/admin` |
| Support console | `http://localhost:8085/m/platform/admin?support=1` → `/support` |

## Sample API calls

```bash
# Health
curl http://localhost:8080/api/health

# Sign in as the demo merchant
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@localhost","password":"Demo@123456"}'

# Public catalogue and shop settings
curl http://localhost:8080/api/public/m/demo/catalog
curl http://localhost:8080/api/public/m/demo/shop-settings

# Merchant orders (use the token from login)
curl http://localhost:8080/api/m/demo/admin/orders -H "Authorization: Bearer <token>"
```

## Automated tests

Backend tests use the separate `test` profile (own H2 database, no demo seed) and create their own
merchants and clients. The Cypress journey (`npm run test:e2e`) relies on this SIT seed. See
[../backend/TESTING.md](../backend/TESTING.md).
