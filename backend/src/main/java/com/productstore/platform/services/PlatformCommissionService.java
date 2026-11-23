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
    return new PayoutDtos.Commission(bp, percent(bp), row.map(c -> c.updatedAt).orElse(null));
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
    return new PayoutDtos.Commission(bp, percent(bp), row.updatedAt);
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
