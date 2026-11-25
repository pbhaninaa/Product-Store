# Security Report: Provider payouts (`feature/provider-payouts`)

**Verdict:** PASS WITH FINDINGS · **Date:** 2026-11-24 · **Reviewer:** security-engineer (read-only, static review) · **Scope:** `git diff SIT...HEAD` (15 commits up to `4264c3f`), plus the existing code these changes depend on

## Summary
| Critical | High | Medium | Low | Info |
|---|---|---|---|---|
| 0 | 0 | 2 | 5 | 4 |

Nothing blocks the release. Authorization on all 8 endpoints is correct and enforced on the server. Owners cannot reach another tenant's data. Bank details only appear in the platform-admin release preview and are never logged or audited. The double-release protection is sound.

The two Medium findings are about the things around the feature:
- **SEC-1:** the payout destination (the shop's bank details) has no change control.
- **SEC-2:** the audit log shows payout data to support staff.

Ticket both before go-live, or have the CTO accept them in writing.

## Attack surface reviewed
- **Entry points:**
  - `SupportPayoutController` (`/api/support`, endpoints 1-7)
  - `AdminEarningsController` (`/api/m/{slug}/admin/store-earnings`, endpoint 8)
  - Paystack webhook and verify fee parsing (`PaystackClient.Transaction.fromJson`, `PaystackPaymentService.toConfirmed`, `OnlinePaymentSettlementService.markPaid`)
  - Test-database reset (`SupportOpsController` calling `SitDangerZoneService.resetRecords`)
  - Frontend routes, nav, `supportApi.js`, `teamApi.js`, and the payout and My income views
- **Trust boundaries:**
  - Browser to API: JWT bearer token in the `Authorization` header, stateless, no cookies, so CSRF does not apply.
  - Paystack to API: webhooks are HMAC-SHA512 signed and checked in constant time (`PaystackPaymentService.java:448-466`). Verify calls go over TLS with the secret key.
- **Data classification:** merchant bank details (financial PII), money amounts and commission (business-confidential), admin email and note (internal).
- **Authorization model:**
  - Spring Security: `/api/support/**` needs SUPPORT_USER or PLATFORM_ADMIN (`SecurityConfig.java:62-63`). Unauthenticated callers get 401 (`:35`).
  - `SupportAccessService.requirePlatformAdmin` (`:33-38`) is the first statement of all 7 support handlers (`SupportPayoutController.java:48,56,62,69,77,89,101`).
  - It checks the role only and ignores support permissions, so a support user who holds every permission is still refused.
  - The owner endpoint calls `requireTenantBySlug` and then `MerchantAccessService.requireOwner` (`AdminEarningsController.java:40-41`). Membership is read from the database, scoped to the path tenant, and staff get `permission_denied`.

## Verified (no finding)
1. **Function-level authorization (AC-5).**
   - All 7 support endpoints are platform-admin only.
   - `ProviderPayoutIntegrationTest.java:120-144` checks 401 with no token and 403 with no data in the body for SUPPORT_USER, MERCHANT_OWNER and CLIENT on every endpoint.
   - The PLATFORM_ADMIN role is only granted by bootstrap (`PlatformAdminBootstrapService.java:79`, `AuthController.java:274`). No support-staff path can grant it.
2. **Owner tenant isolation (AC-18).**
   - The tenant comes only from the authorised path slug. Every query in `StoreEarningsService.forStore` is filtered by that `tenantId`.
   - The owner endpoint takes no payout id or tenant id parameter, so there is nothing to tamper with.
   - The date range only filters within the tenant.
   - The response DTOs (`PayoutDtos.java:99-127`) have no note, `releasedByEmail`, bank fields or attempt or Paystack references. `StoreEarningsIntegrationTest.java:124,130-142` asserts this and the staff, other-owner and unauthenticated refusals.
3. **Admin release integrity.**
   - `attemptIds` must equal the tenant's current payable set exactly, and `payableVersion` must match (`ProviderPayoutService.java:221-228`).
   - The tenant row is locked with `FOR UPDATE` first, under READ_COMMITTED.
   - `UNIQUE(payment_attempt_id)` is the database backstop.
   - `@Check` enforces `fee>=0`, `net>0` and `net = gross - fee - commission`.
4. **Money input.**
   - `feeTotalCents` only accepts integer types or a BigInteger under 64 bits. Strings, decimals and doubles get `payout_fee_invalid`, and negative values are rejected (`SupportPayoutController.java:133-143`, `ProviderPayoutService.java:193`).
   - No overflow is possible. Commission is at most 50% of gross, so `gross - fee - commission` stays within the `long` range, and `net<=0` is rejected.
   - `commissionCents` uses `multiplyExact`.
   - Commission parsing uses `BigDecimal`, accepts 0 to 50 with at most 2 decimals, and turns exponent abuse into `commission_invalid` (`PlatformCommissionService.java:73-88`).
   - All amounts are `long` cents end to end. The frontend parses the fee text as a string into integer cents.
5. **Paystack fee capture.**
   - The fee is only taken from signed webhooks or TLS verify responses.
   - `fees` must be an integral, non-negative number that fits in a long, otherwise it is null (unknown).
   - The only write is `OnlinePaymentSettlementService.java:385-389`, which writes only when the fee is still null.
   - `settleAttempt` returns early once an attempt is PAID, DUPLICATE or NEEDS_REFUND, so the fee is write-once.
   - No controller serialises `OnlinePaymentAttemptEntity`.
6. **Bank details.**
   - Returned only by `preview` (`ProviderPayoutService.java:152-163`).
   - Not logged (`:204`, `:209`, `:273` log tenant id, slug, counts and net only), not in the audit detail (`:278-299`) and not stored on the payout.
   - There is no request or response body logging filter.
   - Spring Security's default `Cache-Control: no-store` headers are active.
7. **Audit content (AC-15, FR-8).**
   - `PAYOUT_RELEASE` records the actor (user id and email), entityId = payout id, merchant, tenantId, amounts, `feeAdjusted` and the reference. It strips `;`, CR and LF to prevent field injection, and is written in the release transaction.
   - `COMMISSION_UPDATE` records `from=…bp;to=…bp` with the actor.
   - Neither contains the note or bank data.
8. **Errors.** On UAT and PROD, `server.error.include-message/stacktrace=never`. Domain errors return `{error: code}`.
9. **Frontend.**
   - The routes have `meta.platformAdminOnly`, the router guard and `isTabVisible` hide the pages, and the code is commented as "UX only" (the server stays the authority).
   - None of the new or changed views or helpers use `localStorage` or `sessionStorage`, `v-html` or `console.*`. Bank details are rendered by Vue interpolation, which escapes them.
   - The owner section only calls `fetchStoreEarnings` when `isStoreOwner`, and the server enforces the same rule anyway.
10. **Test-database reset (AC-20).**
    - The endpoint needs a platform admin (`SupportOpsController.java:210`), the confirm phrase `RESET_DATABASE`, and an active profile in {local, sit, test} (`SitDangerZoneService.java:95-101, 124-126`).
    - UAT and PROD are refused.
11. **Secrets.** The diff contains no key, password or private-key patterns.

## Findings

### SEC-1 Payout destination (shop bank details) has no change control, audit or snapshot (Medium)
- **Location:**
  - `AdminStoreSettingsController.java:99-143` (`PUT /admin/store/banking`: no audit, no notification, no banking-specific timestamp)
  - `ProviderPayoutService.java:152-158` (the preview reads the live values)
  - `ProviderPayoutEntity.java` (no record of where the money was sent)
- **OWASP:** A04:2021 Insecure Design, A09:2021 Security Logging and Monitoring Failures; API6:2023 Unrestricted Access to Sensitive Business Flows
- **Evidence:**
  - Before this feature, the shop bank fields were display-only, for customer EFT.
  - This feature makes them the account the platform pays merchants into.
  - An owner (or anyone holding an owner session) can change them at any moment, and nothing records it.
  - The payout stores no snapshot of the destination. The requirements list "bank-detail snapshot" as an out-of-scope follow-up.
- **Exploit scenario:**
  1. An attacker takes over a merchant owner account (phishing, credential stuffing or a compromised reset mailbox).
  2. They replace the account number with a mule account.
  3. The platform admin opens the release screen, sees complete bank details (no warning, because no field is missing), and pays the mule account outside the app.
  4. The merchant reports non-payment. The app has no record of which account was shown or paid, and no audit entry of the banking change.
- **Recommendation:**
  1. In `updateBanking`, when any bank field changes:
     - write an audit entry with the masked old and new values (bank name plus the last 4 digits);
     - set a new `ShopSettingsEntity.bankDetailsUpdatedAt`;
     - notify the owner (and ideally the other owners).
  2. Return `bankDetailsUpdatedAt` in `ReleasePreview`. If it falls within N days (for example 7), the release UI shows a prominent "Bank details changed on …; confirm with the merchant before paying" warning.
  3. Snapshot the masked destination on `provider_payouts`: `bank_name`, `account_last4`, `account_holder`. Include the last 4 digits in the `PAYOUT_RELEASE` audit detail.
  4. Bring the "bank-detail snapshot" follow-up forward to before go-live, or record a CTO risk acceptance.

### SEC-2 Support staff with VIEW_AUDIT can read payout releases and commission changes (Medium)
- **Location:**
  - `SupportOpsController.java:125-129` (`GET /api/support/audit` uses `requirePermission(VIEW_AUDIT)`)
  - `SupportAuditService.java:37-39` (`listRecent` returns every action)
  - The entries are written at `ProviderPayoutService.java:272` and `PlatformCommissionService.java:67-68`
- **OWASP:** A01:2021 Broken Access Control; API5:2023 Broken Function Level Authorization
- **Evidence:**
  - VIEW_AUDIT can be granted to a SUPPORT_USER (`SupportPermission.java:22`). It is opt-in and not in `DEFAULT_SUPPORT`.
  - The audit list shows `PAYOUT_RELEASE` detail (merchant, tenantId, gross, fee, `feeAdjusted`, commission, net, reference) and `COMMISSION_UPDATE` (old and new rate) for all merchants.
- **Exploit scenario:** A support user who was given VIEW_AUDIT reads every merchant's payout amounts, payment references and the commission history. BR-8 and AC-5 say support staff have no access to payouts or the commission.
- **Recommendation:**
  - Either exclude `PAYOUT_RELEASE` and `COMMISSION_UPDATE` from the audit list unless the caller is PLATFORM_ADMIN, and add an integration test where a VIEW_AUDIT support user does not see them;
  - or have the CTO explicitly accept that the audit-log permission includes finance events, and amend BR-8.

### SEC-3 Support staff can see an owner's store earnings and payouts through a shadow session (Low)
- **Location:** `JwtAuthFilter.java:34-38, 50-55` (GET requests are never shadow-blocked); `ShadowSupportService.mintShadowToken` (USE_SHADOW is in `DEFAULT_SUPPORT`, `SupportPermission.java:30-36`)
- **OWASP:** A01:2021
- **Evidence:** A shadow token is an owner token. `GET /api/m/{slug}/admin/store-earnings` returns the merchant view: owed amount, commission %, payouts received with their references. Plan §9 acknowledges this as an audited existing capability.
- **Exploit scenario:** Any default support user shadows any merchant and reads that merchant's payout history. The data is only the merchant-facing view (no note, no admin email). Entering a shadow session is audited (`SHADOW_ENTER`).
- **Recommendation:** Record the CTO's acceptance in the requirements, because it contradicts BR-8 as worded. If it is not accepted, block `^/api/m/[^/]+/admin/store-earnings$` for shadow sessions, including GET.

### SEC-4 Payout and commission endpoints trust the JWT role claim alone (Low)
- **Location:**
  - `SupportAccessService.java:33-38`
  - Compare `PlatformAdminController.java:50-52`, which re-checks the PLATFORM_ADMIN membership in the database
  - Token lifetime is 12 h (`JwtService.java:27-39`), and removing a membership does not call `revokeTokens()`
- **OWASP:** A07:2021 Identification and Authentication Failures; API5:2023
- **Exploit scenario:** If a platform admin membership is removed, the old token can keep releasing payouts and changing commission for up to 12 h.
- **Recommendation:** In `requirePlatformAdmin`, also check for a PLATFORM_ADMIN membership row, the same as `PlatformAdminController`, or have any membership or role removal call `user.revokeTokens()`.

### SEC-5 Owner store-earnings has no range or size bounds (Low)
- **Location:** `StoreEarningsService.java:197-238`
- **OWASP:** API4:2023 Unrestricted Resource Consumption
- **Evidence:**
  - With no dates ("All dates"), the endpoint loads every payout line and every payout for the tenant into memory. Payment items are cut to 500 after loading. The `payouts` list has no cap.
  - An extreme ISO date such as `+999999999-12-31` makes `end.plusDays(1)` throw `DateTimeException`, which gives a generic 500.
- **Exploit scenario:** A long-lived high-volume store, or repeated "All dates" calls, cause large heap allocations per request. The impact is limited because only the store's own owner can call it.
- **Recommendation:** Cap the range (for example 366 days, or require dates), cap `payouts` (for example 200 with a `truncated` flag), page the line query, and return 400 for out-of-range dates. Apply the same date validation to `GET /api/support/payouts`.

### SEC-6 Test-database reset guard is allow-list only, and the base profile defaults to `local` (Low, pre-existing)
- **Location:** `SitDangerZoneService.java:95-101`; `application.properties:5` (`spring.profiles.active=local`)
- **OWASP:** A05:2021 Security Misconfiguration
- **Evidence:**
  - Reset is allowed whenever `local`, `sit` or `test` is among the active profiles.
  - Nothing refuses it when `prod` or `uat` is also active.
  - A deployment that starts without an explicit profile falls back to `local`.
  - The guard is unchanged from `SIT`. This branch now also deletes payouts and payout lines.
- **Exploit scenario:** A misconfigured deploy (for example `SPRING_PROFILES_ACTIVE=prod,local`) lets a platform admin, or a stolen admin token plus the public confirm phrase, wipe production orders, payments and the payout ledger.
- **Recommendation:**
  - In `wipeAllowed()`, return false whenever any active profile is `prod` or `uat`.
  - Also require an explicit opt-in property (`app.danger-zone.enabled=true`) that is only set in the SIT and local files.
  - Add a unit test for `prod,sit`.

### SEC-7 Public shop-settings endpoint publishes the payout bank account unconditionally (Low, pre-existing)
- **Location:** `PublicShopSettingsController.java:61-64`
- **OWASP:** API3:2023 Broken Object Property Level Authorization
- **Evidence:**
  - The unauthenticated `GET /api/public/m/{slug}/shop-settings` returns bank name, holder, account number and branch code even when the store does not accept customer EFT.
  - These are now the payout destination, and NFR-3 assumes they are only shown to the platform admin.
- **Exploit scenario:** Anyone can scrape the bank details of every store for social engineering or debit-order fraud.
- **Recommendation:** Only include the bank fields when `CustomerPaymentPolicy.acceptEft(s)` is true, and correct NFR-3's wording. This is outside this branch's diff, so ticket it separately.

### Informational
- **I-1:** The `PAYOUT_RELEASE` audit detail has the fee used and `feeAdjusted`, but not `capturedFeeCents`. Add it so that an admin fee adjustment (BR-5, R6) can be reconstructed from the audit trail alone (`ProviderPayoutService.java:278-299`).
- **I-2:** A `PessimisticLockingFailureException` (lock timeout) is always mapped to `payout_already_released` (`ProviderPayoutService.java:208-211`), even when the other transaction rolled back. No integrity impact, but consider a distinct `payout_busy_retry` code.
- **I-3:** A payment that is cancelled or flagged NEEDS_REFUND at the same moment as a release can still be released. Accepted as plan R3 (the clawback follow-up).
- **I-4:** ADR-0002 and plan §7 require confirming `data.fees` and its unit against a real Paystack test-mode payload before merge. This has not been done. If the unit were wrong, fees would be off by a factor of 100. The admin fee-total override mitigates this.

## Dependency scan
**NOT RUN.** A parallel QA build was using the same working tree. The diff changes no `pom.xml`, `package.json` or lockfile, so the feature adds no new dependency risk.

## Limitations
- Static review only. The test suites were not run by this review; test coverage was confirmed by reading the code.
- Existing parts were not re-reviewed beyond what the feature depends on: login brute-force protection, token storage in `localStorage`, and encryption at rest of shop settings.
