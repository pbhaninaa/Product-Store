package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.models.PayoutDtos.Line;
import com.productstore.platform.models.PayoutDtos.Summary;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;

import org.junit.jupiter.api.Test;

class ProviderEarningsServiceTest {

  /** BR-4: commission on the gross, per payment, rounded half-up to the cent. */
  @Test
  void commissionIsTakenOnTheGrossAndRoundedHalfUpPerPayment() {
    assertThat(ProviderEarningsService.commissionCents(100_000, 1000)).isEqualTo(10_000); // AC-7: R1 000 x 10%
    assertThat(ProviderEarningsService.commissionCents(5, 1000)).isEqualTo(1); // 0.5 cent rounds up
    assertThat(ProviderEarningsService.commissionCents(4, 1000)).isZero(); // 0.4 cent rounds down
    assertThat(ProviderEarningsService.commissionCents(15, 1000)).isEqualTo(2); // 1.5 cents
    assertThat(ProviderEarningsService.commissionCents(999, 1250)).isEqualTo(125); // 124.875 cents
    assertThat(ProviderEarningsService.commissionCents(1, 5000)).isEqualTo(1); // 0.5 cent at 50%
    assertThat(ProviderEarningsService.commissionCents(123_456, 0)).isZero(); // 0% means no commission
  }

  @Test
  void summaryCountsUnknownFeesAsZeroAndFlagsThem() {
    Summary s =
        ProviderEarningsService.summarize(
            List.of(line(100_000, 2500L, 10_000), line(50_000, null, 5000), line(20_000, 0L, 2000)));

    assertThat(s.paymentCount()).isEqualTo(3);
    assertThat(s.grossCents()).isEqualTo(170_000);
    assertThat(s.knownFeeCents()).isEqualTo(2500);
    assertThat(s.unknownFeeCount()).isEqualTo(1);
    assertThat(s.commissionCents()).isEqualTo(17_000);
    assertThat(s.netOwedCents()).isEqualTo(170_000 - 2500 - 17_000);
  }

  /** AC-14 / edge case 4: any change to what is owed gives another version; the order of lines does not. */
  @Test
  void payableVersionChangesWithEveryInputThatAffectsTheAmount() {
    Line a = line(100_000, 2500L, 10_000);
    Line b = line(50_000, null, 5000);
    String v = ProviderEarningsService.payableVersion(1000, List.of(a, b));

    assertThat(ProviderEarningsService.payableVersion(1000, List.of(b, a))).isEqualTo(v);
    assertThat(ProviderEarningsService.payableVersion(1250, List.of(a, b))).isNotEqualTo(v);
    assertThat(ProviderEarningsService.payableVersion(1000, List.of(a))).isNotEqualTo(v);
    assertThat(ProviderEarningsService.payableVersion(1000, List.of(a, b, line(1, 0L, 0)))).isNotEqualTo(v);
    Line bKnownZero = new Line(b.attemptId(), b.kind(), b.recordId(), b.recordRef(), b.paymentReference(), b.paidAt(), b.grossCents(), 0L, b.commissionCents());
    assertThat(ProviderEarningsService.payableVersion(1000, List.of(a, bKnownZero))).isNotEqualTo(v);
    Line aOtherGross = new Line(a.attemptId(), a.kind(), a.recordId(), a.recordRef(), a.paymentReference(), a.paidAt(), 100_001, a.feeCents(), a.commissionCents());
    assertThat(ProviderEarningsService.payableVersion(1000, List.of(aOtherGross, b))).isNotEqualTo(v);
  }

  @Test
  void owedForUsesTheCurrentCommissionOnEveryPayment() {
    OnlinePaymentAttemptRepository attempts = mock(OnlinePaymentAttemptRepository.class);
    PlatformCommissionService commission = mock(PlatformCommissionService.class);
    UUID tenantId = UUID.randomUUID();
    OnlinePaymentAttemptEntity paid = new OnlinePaymentAttemptEntity();
    paid.id = UUID.randomUUID();
    paid.kind = OnlinePaymentAttemptEntity.Kind.BOOKING;
    paid.recordId = UUID.randomUUID();
    paid.tenantId = tenantId;
    paid.reference = "BKGX";
    paid.amountCents = 100_000;
    paid.feeCents = 2500L;
    paid.outcome = OnlinePaymentAttemptEntity.Outcome.PAID;
    paid.outcomeAt = Instant.parse("2026-11-20T08:00:00Z");
    when(attempts.findPayableForTenant(tenantId)).thenReturn(List.of(paid));
    when(commission.currentBasisPoints()).thenReturn(1000);

    ProviderEarningsService.Owed owed = new ProviderEarningsService(attempts, commission).owedFor(tenantId);

    assertThat(owed.basisPoints()).isEqualTo(1000);
    assertThat(owed.lines()).singleElement().satisfies(l -> {
      assertThat(l.kind()).isEqualTo("BOOKING");
      assertThat(l.commissionCents()).isEqualTo(10_000);
      assertThat(l.paidAt()).isEqualTo(paid.outcomeAt);
    });
    assertThat(owed.summary().netOwedCents()).isEqualTo(87_500); // AC-7: R1 000 - R25 - R100
    assertThat(owed.payableVersion()).isEqualTo(ProviderEarningsService.payableVersion(1000, owed.lines()));
  }

  private static Line line(long gross, Long fee, long commission) {
    UUID id = UUID.randomUUID();
    return new Line(id, "ORDER", UUID.randomUUID(), "ABCDEF12", "ORD" + id, Instant.now(), gross, fee, commission);
  }
}
