# Security Report: Remove Manual EFT

**Verdict:** PASS WITH FINDINGS · **Date:** 2026-11-23 · **Scope:** `origin/SIT...HEAD` (`ecccdb8..3652f3b`) on `feature/remove-manual-eft` · **Reviewer:** security-engineer (saved by chief-architect)

| Critical | High | Medium | Low | Info |
|---|---|---|---|---|
| 0 | 0 | 2 | 2 | 3 |

## Verified safe
1. No remaining path creates an EFT order or booking (`CustomerPaymentPolicy.acceptEft` always false; explicit refusals in CheckoutService.java:189-191 and SalonBookingService.java:530-534; single creation site for each entity).
2. Legacy approve / reject / confirm endpoints are tenant-scoped and permission-checked (no BOLA/IDOR); cross-tenant test exists.
3. Legacy order proof upload: tenant lock, email match, state checks, magic-byte type detection, rate limit, 12MB cap, 48h expiry.
4. Bank details and EFT instructions removed from public endpoints, invoice, notifications and logs.
5. Subscription `needsPayment` change cannot activate a subscription without a settled Paystack payment.

## Findings
- **SEC-1 (Medium)** Legacy booking proof upload reopens cancelled or past bookings (`SalonBookingService.submitEftProof`, ~:408-453, sets `status = pending` without checking status/startAt). Fix: require `status == pending` and `startAt` in the future; never write status there; `approveManualEftPayment` requires `pending`; integration test. Pre-existing on SIT but now the only live EFT booking path.
- **SEC-2 (Medium)** Payout bank details readable by every staff member (`AdminStoreSettingsController` GET returns the whole entity with `requireMember`). Fix: owner-only bank fields in a DTO (mask/omit for staff), `@JsonIgnore` on entity bank fields, test.
- **SEC-3 (Low here; blocking for provider payouts)** No server-side validation, audit, re-auth or owner notification when payout bank details change. Fix now: Bean Validation on `UpdateBankingRequest`. Before payouts use them: audit + owner notification + release-screen warning for recently changed details (step-up / cooling-off per CTO).
- **SEC-4 (Low, pre-existing, conditional)** Legacy on-disk booking proofs served from public `/uploads`. Check: `SELECT COUNT(*) FROM salon_bookings WHERE payment_proof_path <> ''` (and orders); if any, move under `_private/` and serve via authenticated endpoint; stop building public URLs in `listAdminBookings`.
- **INFO-1** Dead subscription proof endpoint still parses 12MB multipart before refusing. **INFO-2** `forbidden`/`email_mismatch` vs `not_found` existence oracle on legacy proof endpoints. **INFO-3** Bank details were public before this release (possible caches).

Dependency scan: not run (no manifest changes); PDFBox 2.0.37.
