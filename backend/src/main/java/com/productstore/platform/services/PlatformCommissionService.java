package com.productstore.platform.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.productstore.platform.entities.PlatformCommissionEntity;
import com.productstore.platform.models.PayoutDtos;
import com.productstore.platform.repositories.PlatformCommissionRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The platform commission on in-app order and booking payments: 0% to 50% with at most two decimals, stored
 * as basis points (12.5% = 1250), default 0%. Reading never writes; the single row is created on the first
 * save. Every change is audit-logged. Released payouts keep their own snapshot of the rate.
 */
@Service
public class PlatformCommissionService {
  public static final int MAX_BASIS_POINTS = 5_000;
  static final String INVALID = "commission_invalid";
  private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(50);

  private final PlatformCommissionRepository commissions;
  private final SupportAuditService audit;

  public PlatformCommissionService(PlatformCommissionRepository commissions, SupportAuditService audit) {
    this.commissions = commissions;
    this.audit = audit;
  }

  /** The current rate in basis points; 0 until the platform admin sets one. */
  public int currentBasisPoints() {
    return stored().map(c -> c.commissionBasisPoints).orElse(0);
  }

  public PayoutDtos.Commission get() {
    Optional<PlatformCommissionEntity> row = stored();
    int bp = row.map(c -> c.commissionBasisPoints).orElse(0);
    return toDto(row.orElse(null), bp);
  }

  public boolean automaticProviderPayouts() {
    return stored().map(c -> Boolean.TRUE.equals(c.automaticProviderPayouts)).orElse(false);
  }

  public int automaticProviderPayoutDelayDays() {
    Integer days = stored().map(c -> c.automaticProviderPayoutDelayDays).orElse(null);
    if (days == null) return 7;
    return Math.max(0, Math.min(90, days));
  }

  /** 0 = any day; 1 = Monday … 7 = Sunday. */
  public int automaticProviderPayoutWeekday() {
    Integer day = stored().map(c -> c.automaticProviderPayoutWeekday).orElse(null);
    if (day == null || day < 0 || day > 7) return 0;
    return day;
  }

  /**
   * Sets the rate from a percentage (a JSON string or number such as {@code "12.5"}).
   *
   * @throws IllegalArgumentException {@code commission_invalid} for a non-number, below 0, above 50 or more
   *     than two decimals; nothing is stored then
   */
  @Transactional
  public PayoutDtos.Commission set(Object percent, ApiUserPrincipal actor) {
    int bp = parseBasisPoints(percent);
    PlatformCommissionEntity row =
        stored()
            .orElseGet(
                () -> {
                  PlatformCommissionEntity c = new PlatformCommissionEntity();
                  c.id = PlatformCommissionEntity.SINGLETON_ID;
                  return c;
                });
    int from = row.commissionBasisPoints;
    row.commissionBasisPoints = bp;
    row.updatedAt = Instant.now();
    row.updatedByUserId = actor == null ? null : actor.userId();
    commissions.save(row);
    audit.record(
        actor, "COMMISSION_UPDATE", "PLATFORM_COMMISSION", "platform-commission", "from=" + from + "bp;to=" + bp + "bp");
    return toDto(row, bp);
  }

  @Transactional
  public PayoutDtos.Commission setAutomaticPayouts(
      Boolean enabled, Integer delayDays, Integer weekday, ApiUserPrincipal actor) {
    PlatformCommissionEntity row =
        stored()
            .orElseGet(
                () -> {
                  PlatformCommissionEntity c = new PlatformCommissionEntity();
                  c.id = PlatformCommissionEntity.SINGLETON_ID;
                  c.commissionBasisPoints = 0;
                  return c;
                });
    if (enabled != null) {
      row.automaticProviderPayouts = enabled;
    }
    if (delayDays != null) {
      if (delayDays < 0 || delayDays > 90) {
        throw new IllegalArgumentException("payout_delay_invalid");
      }
      row.automaticProviderPayoutDelayDays = delayDays;
    }
    if (weekday != null) {
      if (weekday < 0 || weekday > 7) {
        throw new IllegalArgumentException("payout_weekday_invalid");
      }
      row.automaticProviderPayoutWeekday = weekday;
    }
    row.updatedAt = Instant.now();
    row.updatedByUserId = actor == null ? null : actor.userId();
    commissions.save(row);
    audit.record(
        actor,
        "PAYOUT_SCHEDULE_UPDATE",
        "PLATFORM_COMMISSION",
        "platform-payout-schedule",
        "auto="
            + Boolean.TRUE.equals(row.automaticProviderPayouts)
            + ";delayDays="
            + automaticProviderPayoutDelayDays()
            + ";weekday="
            + automaticProviderPayoutWeekday());
    return toDto(row, row.commissionBasisPoints);
  }

  private PayoutDtos.Commission toDto(PlatformCommissionEntity row, int bp) {
    return new PayoutDtos.Commission(
        bp,
        percent(bp),
        row == null ? null : row.updatedAt,
        row != null && Boolean.TRUE.equals(row.automaticProviderPayouts),
        row == null || row.automaticProviderPayoutDelayDays == null ? 7 : Math.max(0, row.automaticProviderPayoutDelayDays),
        row == null || row.automaticProviderPayoutWeekday == null ? 0 : row.automaticProviderPayoutWeekday);
  }

  /** A percentage (0..50, at most two decimals) as basis points. */
  static int parseBasisPoints(Object raw) {
    BigDecimal pct;
    try {
      if (raw instanceof Number || raw instanceof String) {
        pct = new BigDecimal(String.valueOf(raw).trim());
      } else {
        throw new IllegalArgumentException(INVALID);
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(INVALID);
    }
    if (pct.signum() < 0 || pct.compareTo(MAX_PERCENT) > 0 || pct.stripTrailingZeros().scale() > 2) {
      throw new IllegalArgumentException(INVALID);
    }
    return pct.movePointRight(2).intValueExact();
  }

  /** Basis points as a percentage string with two decimals: 1250 is {@code "12.50"}. */
  public static String percent(int basisPoints) {
    return String.format("%d.%02d", basisPoints / 100, basisPoints % 100);
  }

  private Optional<PlatformCommissionEntity> stored() {
    return commissions.findById(PlatformCommissionEntity.SINGLETON_ID);
  }
}
