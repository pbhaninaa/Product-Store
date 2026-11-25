package com.productstore.platform.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.models.PayoutDtos.Line;
import com.productstore.platform.models.PayoutDtos.Summary;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.util.RefCodes;

import org.springframework.stereotype.Service;

/**
 * The single owed calculation of provider payouts (plan §5). Read only. Used by the support Payouts page, the
 * release screen, the release itself and the store owner's earnings, so they always agree.
 *
 * <p>Payable (BR-1, BR-2): a PAID order or booking payment attempt in no payout whose order or booking is
 * not cancelled. Commission (BR-4) is taken on the gross per payment, rounded half-up to the cent. Net =
 * gross - Paystack fees - commission (BR-3); an unknown fee counts as 0 here and is flagged (BR-5).
 */
@Service
public class ProviderEarningsService {
  /** Payment dates and "last 7 days" are South African calendar days (A1). */
  public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

  /** One merchant's owed payments at the commission rate {@code basisPoints}. */
  public record Owed(UUID tenantId, int basisPoints, List<Line> lines, Summary summary, String payableVersion) {}

  private final OnlinePaymentAttemptRepository attempts;
  private final PlatformCommissionService commission;

  public ProviderEarningsService(OnlinePaymentAttemptRepository attempts, PlatformCommissionService commission) {
    this.attempts = attempts;
    this.commission = commission;
  }

  /** Every merchant with something owed, at the commission rate {@code basisPoints}. */
  public record OwedAll(int basisPoints, List<Owed> merchants) {}

  /** Every merchant with something owed (not date limited), in one query. */
  public OwedAll owedByMerchant() {
    int bp = commission.currentBasisPoints();
    Map<UUID, List<Line>> byTenant = new LinkedHashMap<>();
    for (OnlinePaymentAttemptEntity a : attempts.findPayable()) {
      byTenant.computeIfAbsent(a.tenantId, t -> new ArrayList<>()).add(toLine(a, bp));
    }
    List<Owed> out = new ArrayList<>(byTenant.size());
    byTenant.forEach((tenantId, lines) -> out.add(owed(tenantId, bp, lines)));
    return new OwedAll(bp, out);
  }

  /** One merchant's owed payments at the current commission (an empty list when nothing is owed). */
  public Owed owedFor(UUID tenantId) {
    int bp = commission.currentBasisPoints();
    List<Line> lines = attempts.findPayableForTenant(tenantId).stream().map(a -> toLine(a, bp)).toList();
    return owed(tenantId, bp, lines);
  }

  private static Owed owed(UUID tenantId, int bp, List<Line> lines) {
    return new Owed(tenantId, bp, lines, summarize(lines), payableVersion(bp, lines));
  }

  /**
   * The commission on one payment: {@code gross x bp / 10 000}, rounded half-up to the cent. This is the
   * only place the base (the gross payment) is chosen.
   */
  public static long commissionCents(long grossCents, int basisPoints) {
    return Math.floorDiv(Math.multiplyExact(grossCents, (long) basisPoints) + 5_000, 10_000);
  }

  /** Totals of the lines; the net uses the known fees (unknown fees count as 0 and are counted). */
  public static Summary summarize(List<Line> lines) {
    long gross = 0;
    long knownFee = 0;
    long commissionTotal = 0;
    int unknown = 0;
    for (Line l : lines) {
      gross += l.grossCents();
      commissionTotal += l.commissionCents();
      if (l.feeCents() == null) {
        unknown++;
      } else {
        knownFee += l.feeCents();
      }
    }
    return new Summary(lines.size(), gross, knownFee, unknown, commissionTotal, gross - knownFee - commissionTotal);
  }

  /**
   * A fingerprint of what is owed: the commission rate and every payment's id, gross and captured fee. Any
   * payment added or removed, a fee change or a commission change gives another version (AC-14).
   */
  public static String payableVersion(int basisPoints, List<Line> lines) {
    StringBuilder sb = new StringBuilder("bp=").append(basisPoints);
    lines.stream()
        .sorted(Comparator.comparing(l -> l.attemptId().toString()))
        .forEach(
            l ->
                sb.append('|')
                    .append(l.attemptId())
                    .append(':')
                    .append(l.grossCents())
                    .append(':')
                    .append(l.feeCents() == null ? "null" : l.feeCents().toString()));
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("sha256_unavailable", e);
    }
  }

  static Line toLine(OnlinePaymentAttemptEntity a, int bp) {
    return new Line(
        a.id,
        a.kind.name(),
        a.recordId,
        RefCodes.shortRef(a.recordId),
        a.reference,
        a.outcomeAt != null ? a.outcomeAt : a.startedAt,
        a.amountCents,
        a.feeCents,
        commissionCents(a.amountCents, bp));
  }

  /** Start of a South African calendar day. */
  public static Instant startOfDay(LocalDate day) {
    return day.atStartOfDay(ZONE).toInstant();
  }

  /** Dates are given both or neither (same code as payroll). */
  public static void requireBothOrNeither(LocalDate start, LocalDate end) {
    if ((start == null) != (end == null)) {
      throw new IllegalArgumentException("provide_both_start_and_end_or_neither");
    }
  }
}
