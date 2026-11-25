# ADR-0002: Capture the Paystack fee on the payment attempt at settlement

- **Status:** Proposed
- **Date:** 2026-11-23
- **Deciders:** solution-architect, chief-architect
- **Related:** `docs/engineering/requirements/provider-payouts.md` (FR-3, BR-3, BR-5, AC-6, A2), `docs/engineering/plans/provider-payouts.md` §7, ADR-0001

## Context
The provider bears the Paystack fee (BR-3), so the fee of each order or booking payment must be known when computing what is owed. The fee is stored once (a repeated webhook or verify must not change it). A missing fee is stored as **unknown**, never as R0 (AC-6). There is no back-fill (A2).

From the code:
- Both confirmation paths parse the full Paystack `data` object through `PaystackClient.Transaction.fromJson` (services/PaystackClient.java:84-105): the signed webhook at services/PaystackPaymentService.java:464 and verify at services/HttpPaystackClient.java:79. The `fees` field is in reach but not read today.
- Settlement of order and booking payments goes through `OnlinePaymentSettlementService.settleAttempt` (services/OnlinePaymentSettlementService.java:235-258). That method locks the attempt row and sets its outcome to PAID exactly once (early return for PAID, lines 236-245), through `markOutcome` (lines 365-369).

## Options considered
| Option | Pros | Cons |
|--------|------|------|
| A. Parse `data.fees` into `Transaction`, carry it on `ConfirmedPayment`, write `online_payment_attempts.fee_cents` (nullable) in `markOutcome` when the attempt becomes PAID and the fee is still null | Same locked transaction as settlement, so no extra Paystack call. Write-once by construction. The payable ledger (attempts) holds everything the owed calculation needs. | Small changes to a sensitive settlement class (covered by tests). |
| B. Store the fee on `orders` / `salon_bookings` | Close to the business record | Duplicate payments and earlier attempts would collide on one column. Two tables to change. The ledger is the attempt, not the record. |
| C. Fetch fees later (at payout time) with a Paystack verify per payment | No settlement change | N external calls on the admin page (NFR-5 at risk). It depends on Paystack being available during a release. The verify cache and rate limits add to the load. |
| D. Compute the fee from Paystack's published tariff | No API dependency | Wrong for card, EFT and international mixes, caps and tariff changes. It invents money figures. |

## Decision
**Option A.**
- Add `Long feesCents` to `Transaction`. It is null when `fees` is absent, null, not an integer or negative.
- `PaystackPaymentService.toConfirmed` passes the fee the provider actually bears: `max(0, fees - max(0, charged - requested))`. If charges were passed to the customer (`amount > requested_amount`), that surcharge is not charged to the provider. Normally this is just `fees`.
- `ConfirmedPayment` gets a nullable `feeCents` (the existing 7-arg constructor passes null).
- `markOutcome(…, PAID, payment)` sets `fee_cents` only if it is null.
- DUPLICATE and NEEDS_REFUND attempts get no fee (they are never payable).
- Merchant subscription payments are out of scope (platform revenue, BR-1).

## Consequences
- Positive: the fee is captured atomically with settlement, with no extra I/O. Unknown and zero are distinguishable. The owed calculation reads one table.
- Negative / trade-offs accepted:
  - Payments settled before this change, or through the legacy no-attempt path, have no fee, or no attempt at all (see plan R1). The admin adjusts the fee total at release (BR-5).
  - If a first confirmation lacked `fees` and a later one has it, the fee stays unknown. This is the "stored once" rule, chosen over an update path.
- Follow-up actions: confirm the `fees` field and unit against a real Paystack test-mode `charge.success` and verify payload, and use them as test fixtures before merge.
