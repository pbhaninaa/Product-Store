package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * The booking fee business rules (requirements/booking-fee.md), free of I/O so they are unit tested on their own.
 *
 * <ul>
 *   <li>BR-1: a whole percent from 0 to 50; 0 means no fee.
 *   <li>NFR-1: fee rounded half up to the cent; remainder = price minus fee, so the parts add up to the total.
 *   <li>BR-4/BR-5: a customer cancelling at least 24 hours before the start gets the fee back; later, the provider
 *       keeps it. Exactly 24 hours is refunded.
 * </ul>
 */
public final class BookingFeePolicy {
  public static final int MIN_PERCENT = 0;
  public static final int MAX_PERCENT = 50;
  /** Customers cancelling at least this long before the start get the fee back (BR-4). */
  public static final Duration FREE_CANCELLATION_NOTICE = Duration.ofHours(24);

  static final String OUT_OF_RANGE = "booking_fee_percent_out_of_range";

  private BookingFeePolicy() {}

  /**
   * Validates a percent a provider wants to save.
   *
   * @throws IllegalArgumentException {@code booking_fee_percent_out_of_range} when missing, fractional, below 0
   *     or above 50
   */
  public static int validatePercent(BigDecimal raw) {
    if (raw == null) throw new IllegalArgumentException(OUT_OF_RANGE);
    BigDecimal value = raw.stripTrailingZeros();
    if (value.scale() > 0) throw new IllegalArgumentException(OUT_OF_RANGE);
    if (value.compareTo(BigDecimal.valueOf(MIN_PERCENT)) < 0 || value.compareTo(BigDecimal.valueOf(MAX_PERCENT)) > 0) {
      throw new IllegalArgumentException(OUT_OF_RANGE);
    }
    return value.intValueExact();
  }

  /** The stored setting as an effective percent: null or anything out of range counts as 0 (no fee). */
  public static int effectivePercent(Integer stored) {
    if (stored == null || stored < MIN_PERCENT || stored > MAX_PERCENT) return 0;
    return stored;
  }

  /** {@code price x percent / 100}, rounded half up to the cent. Zero for a missing or non-positive price. */
  public static BigDecimal feeFor(BigDecimal price, int percent) {
    if (price == null || price.signum() <= 0 || percent <= 0) return BigDecimal.ZERO.setScale(2);
    return price
        .multiply(BigDecimal.valueOf(percent))
        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  /** What is left to pay at the shop: price minus fee (never negative). */
  public static BigDecimal remainder(BigDecimal price, BigDecimal fee) {
    BigDecimal p = price == null ? BigDecimal.ZERO : price;
    BigDecimal f = fee == null ? BigDecimal.ZERO : fee;
    return p.subtract(f).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
  }

  /** True when a customer cancelling at {@code now} gets the fee back: at least 24 hours before the start. */
  public static boolean customerCancellationRefunds(Instant startAt, Instant now) {
    if (startAt == null || now == null) return false;
    return !Duration.between(now, startAt).minus(FREE_CANCELLATION_NOTICE).isNegative();
  }
}
