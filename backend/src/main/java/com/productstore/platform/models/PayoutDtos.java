package com.productstore.platform.models;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * API shapes of provider payouts and the platform commission (plan §4). All money is integer cents;
 * percentages are strings with two decimals next to the basis points. A null {@code feeCents} means the
 * Paystack fee is unknown (never shown as zero).
 */
public final class PayoutDtos {
  private PayoutDtos() {}

  // ------------------------------------------------------------------ platform admin

  public record Commission(
      int commissionBasisPoints,
      String commissionPercent,
      Instant updatedAt,
      boolean automaticProviderPayouts,
      int automaticProviderPayoutDelayDays,
      int automaticProviderPayoutWeekday) {}

  public record OwedMerchant(
      UUID tenantId,
      String merchantName,
      String merchantSlug,
      int paymentCount,
      long grossCents,
      long knownFeeCents,
      int unknownFeeCount,
      long commissionCents,
      long netOwedCents,
      Instant oldestPaidAt) {}

  public record OwedTotals(
      int paymentCount, long grossCents, long knownFeeCents, long commissionCents, long netOwedCents) {}

  public record OwedList(
      int commissionBasisPoints, String commissionPercent, List<OwedMerchant> merchants, OwedTotals totals) {}

  public record Merchant(UUID tenantId, String name, String slug) {}

  /** Shown only to the platform admin on the release screen; never logged, audited or stored on a payout. */
  public record BankDetails(String bankName, String accountHolder, String accountNumber, String branchCode) {}

  /** One payment (owed, or covered by a payout). {@code kind} is ORDER or BOOKING. */
  public record Line(
      UUID attemptId,
      String kind,
      UUID recordId,
      String recordRef,
      String paymentReference,
      Instant paidAt,
      long grossCents,
      Long feeCents,
      long commissionCents) {}

  public record Summary(
      int paymentCount,
      long grossCents,
      long knownFeeCents,
      int unknownFeeCount,
      long commissionCents,
      long netOwedCents) {}

  public record ReleasePreview(
      Merchant merchant,
      BankDetails bankDetails,
      List<String> missingBankFields,
      int commissionBasisPoints,
      String commissionPercent,
      List<Line> payments,
      Summary summary,
      String payableVersion) {}

  public record Payout(
      UUID id,
      UUID tenantId,
      String merchantName,
      String merchantSlug,
      Instant releasedAt,
      int paymentCount,
      long grossCents,
      long capturedFeeCents,
      int unknownFeeCount,
      long feeCents,
      boolean feeAdjusted,
      int commissionBasisPoints,
      String commissionPercent,
      long commissionCents,
      long netCents,
      String reference,
      String note,
      String releasedByEmail) {}

  public record PayoutDetail(Payout payout, List<Line> lines) {}

  public record PayoutPage(List<Payout> payouts, int page, int size, long total) {}

  // ------------------------------------------------------------------ store owner (no admin-internal data)

  public record StoreEarnings(Range range, StorePayments payments, StoreOwed owed, List<StorePayout> payouts) {}

  public record Range(LocalDate startDate, LocalDate endDate) {}

  /** {@code status} is OWED or PAID_OUT. */
  public record StorePayment(
      Instant paidAt, String kind, String recordRef, long grossCents, Long feeCents, String status) {}

  public record StorePayments(List<StorePayment> items, int count, long grossCents, boolean truncated) {}

  public record StoreOwed(
      int paymentCount,
      long grossCents,
      long knownFeeCents,
      int unknownFeeCount,
      String commissionPercent,
      long commissionCents,
      long netOwedCents) {}

  public record StorePayout(
      UUID id,
      Instant releasedAt,
      int paymentCount,
      long grossCents,
      long feeCents,
      String commissionPercent,
      long commissionCents,
      long netCents,
      String reference) {}
}
