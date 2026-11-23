package com.productstore.platform.services;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.ProviderPayoutEntity;
import com.productstore.platform.entities.ProviderPayoutLineEntity;
import com.productstore.platform.models.PayoutDtos;
import com.productstore.platform.models.PayoutDtos.Line;
import com.productstore.platform.repositories.ProviderPayoutLineRepository;
import com.productstore.platform.repositories.ProviderPayoutRepository;
import com.productstore.platform.services.ProviderEarningsService.Owed;
import com.productstore.platform.util.RefCodes;

import org.springframework.stereotype.Service;

/**
 * A store owner's in-app earnings (AC-17, AC-18): the store's in-app payments settled in the range (owed or
 * paid out), what the platform still owes (all unreleased payments, not range limited, same calculation as
 * the Payouts page) and the payouts released in the range. Never contains the admin's note, the releasing
 * admin, bank details or another store's data: the caller passes the tenant it authorised.
 */
@Service
public class StoreEarningsService {
  /** Payment rows returned at most; totals always cover every payment in the range. */
  public static final int MAX_PAYMENT_ITEMS = 500;

  static final String OWED = "OWED";
  static final String PAID_OUT = "PAID_OUT";

  private final ProviderEarningsService earnings;
  private final ProviderPayoutRepository payouts;
  private final ProviderPayoutLineRepository payoutLines;

  public StoreEarningsService(
      ProviderEarningsService earnings, ProviderPayoutRepository payouts, ProviderPayoutLineRepository payoutLines) {
    this.earnings = earnings;
    this.payouts = payouts;
    this.payoutLines = payoutLines;
  }

  /** {@code start}/{@code end} are SAST calendar days, both or neither (neither = no date limit). */
  public PayoutDtos.StoreEarnings forStore(UUID tenantId, LocalDate start, LocalDate end) {
    ProviderEarningsService.requireBothOrNeither(start, end);
    Instant from = start == null ? null : ProviderEarningsService.startOfDay(start);
    Instant to = end == null ? null : ProviderEarningsService.startOfDay(end.plusDays(1));

    Owed owed = earnings.owedFor(tenantId);

    List<PayoutDtos.StorePayment> payments = new ArrayList<>();
    for (Line l : owed.lines()) {
      if (inRange(l.paidAt(), from, to)) {
        payments.add(
            new PayoutDtos.StorePayment(l.paidAt(), l.kind(), l.recordRef(), l.grossCents(), l.feeCents(), OWED));
      }
    }
    List<ProviderPayoutLineEntity> paidOut =
        from == null
            ? payoutLines.findByTenantId(tenantId)
            : payoutLines.findByTenantIdAndPaidAtGreaterThanEqualAndPaidAtLessThan(tenantId, from, to);
    for (ProviderPayoutLineEntity l : paidOut) {
      payments.add(
          new PayoutDtos.StorePayment(
              l.paidAt, l.kind.name(), RefCodes.shortRef(l.recordId), l.grossCents, l.feeCents, PAID_OUT));
    }
    payments.sort(Comparator.comparing(PayoutDtos.StorePayment::paidAt, Comparator.nullsLast(Comparator.reverseOrder())));
    long gross = payments.stream().mapToLong(PayoutDtos.StorePayment::grossCents).sum();
    boolean truncated = payments.size() > MAX_PAYMENT_ITEMS;

    var s = owed.summary();
    PayoutDtos.StoreOwed owedDto =
        new PayoutDtos.StoreOwed(
            s.paymentCount(),
            s.grossCents(),
            s.knownFeeCents(),
            s.unknownFeeCount(),
            PlatformCommissionService.percent(owed.basisPoints()),
            s.commissionCents(),
            s.netOwedCents());

    List<ProviderPayoutEntity> released =
        from == null
            ? payouts.findByTenantIdOrderByReleasedAtDescIdDesc(tenantId)
            : payouts.findByTenantIdAndReleasedAtGreaterThanEqualAndReleasedAtLessThanOrderByReleasedAtDescIdDesc(
                tenantId, from, to);

    return new PayoutDtos.StoreEarnings(
        new PayoutDtos.Range(start, end),
        new PayoutDtos.StorePayments(
            truncated ? List.copyOf(payments.subList(0, MAX_PAYMENT_ITEMS)) : List.copyOf(payments),
            payments.size(),
            gross,
            truncated),
        owedDto,
        released.stream().map(StoreEarningsService::toStorePayout).toList());
  }

  private static PayoutDtos.StorePayout toStorePayout(ProviderPayoutEntity p) {
    return new PayoutDtos.StorePayout(
        p.id,
        p.releasedAt,
        p.paymentCount,
        p.grossCents,
        p.feeCents,
        PlatformCommissionService.percent(p.commissionBasisPoints),
        p.commissionCents,
        p.netCents,
        p.reference);
  }

  private static boolean inRange(Instant when, Instant from, Instant to) {
    if (from == null) return true;
    return when != null && !when.isBefore(from) && when.isBefore(to);
  }
}
