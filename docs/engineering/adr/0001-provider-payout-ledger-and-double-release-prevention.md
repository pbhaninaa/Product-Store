# ADR-0001: Provider payout ledger and double-release prevention

- **Status:** Proposed
- **Date:** 2026-11-23
- **Deciders:** solution-architect, chief-architect
- **Related:** `docs/engineering/requirements/provider-payouts.md` (FR-5, FR-6, BR-6, NFR-1, NFR-2, AC-12..AC-14), `docs/engineering/plans/provider-payouts.md` §3, §6

## Context
The platform admin records a payout to a merchant after paying them outside the app. Each in-app order or booking payment (a PAID `online_payment_attempts` row) may be in **at most one** payout, including when two admins release at once, on a double-click and on a retried request (BR-6, NFR-2). A release must be all-or-nothing. It must be rejected if the owed set changed since the admin opened the screen (AC-14). It must snapshot what was paid (FR-6).

Constraints:
- Hibernate `ddl-auto=update` with no migration tool, so constraints must be expressible on JPA entities.
- H2 in tests and SIT, MySQL 8 on UAT and PROD. The guarantee must hold on both.
- The project has no DB foreign keys (entities hold raw UUIDs).
- Money is integer cents.

## Options considered
| Option | Pros | Cons |
|--------|------|------|
| A. `provider_payouts` + `provider_payout_lines` with **UNIQUE(payment_attempt_id)**. Per-merchant `SELECT … FOR UPDATE` on the tenant row as the first statement of a READ_COMMITTED transaction. Client sends `attemptIds` + `payableVersion` (hash of lines and commission bp). Unique-key and lock errors are translated to 409. | DB-level guarantee on both engines. Per-line snapshot (gross, fee, commission) gives exact history. Separate messages for "already released" (AC-13) and "amounts changed" (AC-14). Additive tables only. | Two tables. Correctness of the lock depends on it being the first statement (documented and tested). |
| B. Nullable `payout_id` column on `online_payment_attempts`, claimed with `UPDATE … SET payout_id=? WHERE id IN (…) AND payout_id IS NULL` and an affected-row-count check | One table less. The conditional update is atomic on both engines. | No per-line snapshot, so commission per line has to be recomputed. It mixes payout state into the payment-settlement table that `OnlinePaymentSettlementService` locks and updates. A large IN list. "Already released" vs "changed" is harder to tell apart. |
| C. Optimistic `@Version` on a per-merchant "payout cursor" row, no unique key | Simple, no pessimistic lock | No DB guarantee that a payment is in only one payout (BR-6 needs one). Needs a cursor row per merchant to be maintained. |
| D. Client idempotency key (unique `request_key` on payouts) only | Handles retries of the same request | Does not stop two different admins (two keys) from releasing the same payments. It would still need A or B. |

## Decision
**Option A.**
- **The guarantee**: `uk_provider_payout_lines_attempt` makes a second payout containing the same payment impossible at the database level, whatever the timing (BR-6).
- **Making it the normal path**: the per-merchant tenant-row lock (`TenantRepository.lockById`, PESSIMISTIC_WRITE, issued first) serialises releases, so the unique key is a backstop rather than the normal path.
  - READ_COMMITTED (per transaction; MySQL honours it, and it is H2's default) means the waiting release sees the committed lines and answers `payout_already_released` cleanly.
  - If engines differ (H2 MVStore may raise a lock timeout or a duplicate key instead of waiting), the translation of `DataIntegrityViolationException`, `PessimisticLockingFailureException` and `CannotAcquireLockException` to 409 `payout_already_released` covers it.
- **Stale screens (AC-14)**: the `payableVersion` hash covers the attempt ids, gross, captured fees and the commission bp.
- **Idempotency**: a retried request finds its payments already released and gets 409, so it creates no second payout. A separate idempotency-key column (D) would add nothing that A does not already give.
- **Line snapshots**: lines store gross, captured fee (nullable) and per-payment rounded commission. Payout totals are sums of the lines. A `@Check` enforces `net = gross - fee - commission`, `fee >= 0` and `net > 0` (NFR-1).

## Consequences
- Positive: integrity is enforced by the DB on both engines, history is exact and auditable, and there are no changes to the settlement write path.
- Negative / trade-offs accepted:
  - Releases for one merchant are serialised, which is negligible (ms, admin-only).
  - The owed query uses `NOT EXISTS` over lines, so it scans all PAID attempts as history grows. This is acceptable at NFR-5 volumes. If it is measured as slow, add a denormalised flag later.
  - No FK from lines to attempts (project convention). The test reset deletes lines before attempts.
- Follow-up actions:
  - Concurrency integration test on H2 and MySQL (Testcontainers).
  - Payouts are never deleted except by the test reset (NFR-6).
  - Clawback after refund stays a follow-up.
