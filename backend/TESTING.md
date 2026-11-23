# Testing — Product-Store

CI (`.github/workflows/ci.yml`) runs every layer on each push to `SIT`, `UAT`, `PROD` and `feature/**`.

| Layer | Where | Run locally | What it proves |
|-------|-------|-------------|----------------|
| Backend unit + integration | `backend/src/test` | `cd backend && mvn test` | Services, security matrix, signup, subscriptions/trial, order lifecycle, support console, config parsing |
| Backend end-to-end | `backend/src/test/.../e2e/StorePaystackE2ETest.java` | `mvn test -Dtest=StorePaystackE2ETest` | Full HTTP journeys (see below) |
| Frontend unit / integration / regression | `frontend/tests` | `cd frontend && npm test` | Payment labels, delete policy, session expiry, nav labels |
| Browser journey | `frontend/cypress/e2e/storeJourney.cy.ts` | see below | Real UI against a running SIT stack |
| Mobile | `mobile/src/**/__tests__` | `cd mobile && npx jest` | WebView URL allowlist, per-environment endpoints |

All backend tests use the `test` profile (in-memory H2, `src/test/resources/application-test.properties`); no MySQL or network is needed.

## Backend end-to-end journeys

`StorePaystackE2ETest` drives the real API with MockMvc, the way a browser and Paystack would. The Paystack API
client is replaced by a test double, so no network is needed:

1. **Merchant → paid order:** merchant registers → adds a product (stock 5) → client registers → places an online order (the initialize request is checked: amount in cents, ZAR, card channel, callback URL, metadata) → webhooks with a bad signature or a wrong amount are refused → a signed `charge.success` (sent twice, as Paystack retries) marks the order paid → stock drops to 3 exactly once → merchant and client both see it paid → merchant marks it ready.
2. **Concurrent webhooks:** four identical webhooks at once all return `200` and stock drops once. On H2 this passes even without the row lock (H2 does not reproduce the interleaving); production relies on the MySQL row lock in `OrderRepository.lockBy…`.
3. **Booking and subscription:** a salon booking and, after the 7-day trial expires, a merchant subscription are paid with signed webhooks.
4. **Return page:** the payment-status endpoint asks Paystack to verify a still pending payment and settles it when the webhook is late; it accepts the Paystack reference instead of the customer's email.
5. **No webhook at all:** the reconciliation service settles a paid order from Paystack's verify answer and leaves an abandoned one pending.
6. **Payment cannot start:** the just-placed order or booking is cancelled, giving back stock, the promotion use and the salon slot.

`LegacyPaymentSchemaMigrationMySqlTest` runs the one-time payment schema migration against a real MySQL 8 with Testcontainers.
It needs Docker and is skipped (not failed) when Docker is not running.

Webhooks are signed exactly as Paystack signs them: hex HMAC SHA512 of the raw body with the secret key, in `x-paystack-signature`.

## Browser journey (Cypress)

Needs the SIT stack running (H2 + SIT-only demo seed: store `demo`, merchant `demo@localhost` / `Demo@123456`):

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=sit   # :8080
cd frontend && npm run serve                                       # :8085
cd frontend && npm run test:e2e
```

Covers: customer adds "Demo candle" and places a cash pickup order → merchant signs in and sees it → an expired session drops back to the sign-in form. Paystack's hosted page is external, so the paid path is covered by the backend journey above.
