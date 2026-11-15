# Testing — Product-Store

CI (`.github/workflows/ci.yml`) runs every layer on each push to `SIT`, `UAT`, `PROD` and `feature/**`.

| Layer | Where | Run locally | What it proves |
|-------|-------|-------------|----------------|
| Backend unit + integration | `backend/src/test` | `cd backend && mvn test` | Services, security matrix, signup, subscriptions/trial, order lifecycle, support console, config parsing |
| Backend end-to-end | `backend/src/test/.../e2e/StorePayFastE2ETest.java` | `mvn test -Dtest=StorePayFastE2ETest` | Full HTTP journeys (see below) |
| Frontend unit / integration / regression | `frontend/tests` | `cd frontend && npm test` | Payment labels, delete policy, session expiry, nav labels |
| Browser journey | `frontend/cypress/e2e/storeJourney.cy.ts` | see below | Real UI against a running SIT stack |
| Mobile | `mobile/src/**/__tests__` | `cd mobile && npx jest` | WebView URL allowlist, per-environment endpoints |

All backend tests use the `test` profile (in-memory H2, `src/test/resources/application-test.properties`); no MySQL or network is needed.

## Backend end-to-end journeys

`StorePayFastE2ETest` drives the real API with MockMvc, the way a browser and PayFast would:

1. **Merchant → paid order:** merchant registers → adds a product (stock 5) → client registers → places a PayFast order → tampered-signature and under-paid ITNs are refused → a signed ITN (sent twice, as PayFast retries) marks the order paid → stock drops to 3 exactly once → merchant and client both see it paid → merchant marks it ready.
2. **Concurrent ITNs:** four identical ITNs at once all return `200` and stock drops once. On H2 this passes even without the row lock (H2 does not reproduce the interleaving); production relies on the MySQL row lock in `OrderRepository.lockBy…`.
3. **Subscription:** after the 7-day trial expires, the merchant pays with PayFast; the subscription ITN (sent twice) activates the plan.

PayFast ITNs are simulated by signing the form fields with `PayFastSignatureUtil` and the test passphrase, exactly as PayFast posts to `/api/payments/payfast/webhook`.

## Browser journey (Cypress)

Needs the SIT stack running (H2 + SIT-only demo seed: store `demo`, merchant `demo@localhost` / `Demo@123456`):

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=sit   # :8080
cd frontend && npm run serve                                       # :8085
cd frontend && npm run test:e2e
```

Covers: customer adds "Demo candle" and places a cash pickup order → merchant signs in and sees it → an expired session drops back to the sign-in form. PayFast's hosted page is external, so the paid path is covered by the backend journey above.
