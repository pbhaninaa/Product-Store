# Code Review: Remove Manual EFT

**Verdict:** CHANGES REQUESTED · **Date:** 2026-11-23 · **Range:** `origin/SIT...HEAD` (`ecccdb8..3652f3b`) · **Reviewer:** code-reviewer (saved by chief-architect)

Evidence at review time: backend 354 tests / 0 failures; frontend 28 files / 193 tests; lint clean.

| # | Severity | Location | Problem | Correction |
|---|---|---|---|---|
| 1 | Major | `frontend/src/utils/errorMessages.js` | `eft_proof_amount_or_reference_mismatch` message deleted but still thrown by live legacy approval paths (`CheckoutService.java:410`, `SalonBookingService.java:512`) → generic error. | Restore merchant-worded message + vitest; backend test approving a mismatching PDF proof. |
| 2 | Major | `SalonBookingService.java:263-266`, `CheckoutService.java:178-194`, `SalonBookView.vue`, `AdminStoreSettingsView.vue` | Shops with only EFT enabled silently lose all payment methods on deploy; no merchant warning; booking page disables submit without explanation. | Pre-deploy count + CTO decision; Store settings warning when cash and online are both off; SalonBookView alert when no payment method. |
| 3 | Major | `SalonBookingService.submitEftProof` (~418-449) | Can reopen cancelled/past legacy bookings (= SEC-1). | Require `pending` + future `startAt`; no status writes; integration test. |
| 4 | Minor | `CheckoutService.java:179,189-191`; `SalonBookingService.java:263-269,528-535` | EFT refusal via constant-false policy, after locks/stock checks → wrong error codes. | Refuse `eft` first, unconditionally; remove `allowEft`/`shopEft`; tests. |
| 5 | Minor | `AdminStoreSettingsController.java:55-60` | Staff can read owner payout bank details (= SEC-2). | Owner-only DTO / masking + `@JsonIgnore`; test. |
| 6 | Minor | `AdminStoreSettingsController.java:109-117` | No server-side validation of payout fields (= SEC-3). | Bean Validation + all-or-nothing check. |
| 7 | Minor | `SalonBookingService.java:751-769`, `AdminSalonBookingsView.vue` | Legacy `awaiting_proof` bookings never expire. | Extend expiry job or admin hint. |
| 8 | Minor | `ApiExceptionHandler.java:35`, `MerchantSubscriptionService.java`, `PublicSalonController.java:192`, `CheckoutService.java:345-352`, `SalonBookingService.java:441-460` | Dead code (`autoOk` branches, retired platform-banking flags, `paymentReferenceHint`). | Remove unreachable branches; list deprecated fields for next release in the plan note. |
| 9 | Nit | `MerchantSubscriptionApiIntegrationTest.java` | Fully-qualified enum. | Import. |
| 10 | Nit | `faqs/product-store-faqs.json:43` | Invalid UTF-8 (pre-existing). | Follow-up. |

Verified correct: no EFT creation path remains; `needsPayment` logic; legacy flows; contract matches the note; deploy order independent; commits conventional with BREAKING CHANGE footer.
