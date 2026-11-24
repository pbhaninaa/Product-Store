package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.productstore.platform.constants.EmployeeOrderPayMethod;
import com.productstore.platform.constants.EmployeePayMethod;
import com.productstore.platform.entities.EmployeeEntity;
import com.productstore.platform.entities.OrderEntity;

/**
 * Employee pay rules: setting validation and the pay for one job. Employee pay is held as rand {@code Double}
 * (like {@code payRate}); every amount is worked out in {@link BigDecimal} and rounded half up to the cent
 * once, at the end.
 *
 * <ul>
 *   <li>Service pay ({@link EmployeePayMethod}) applies to salon bookings. {@code PERCENT_OF_SERVICE} is a
 *       percentage of the booking's {@code amountZar} (service price less discount).
 *   <li>Order pay ({@link EmployeeOrderPayMethod}) applies to orders credited to the employee. {@code
 *       PERCENT_OF_ORDER} is a percentage of {@link #orderPayBase}: the order total after discounts,
 *       excluding the delivery fee.
 *   <li>The employee's bonus percentage is added on top of both.
 * </ul>
 */
public final class EmployeePayRules {
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final double PERCENT_MAX = 100.0;

  private EmployeePayRules() {}

  /** Service pay method name; blank means PER_SERVICE. */
  public static String normalizeServicePayMethod(String raw) {
    if (raw == null || raw.isBlank()) return EmployeePayMethod.PER_SERVICE.name();
    String u = raw.trim().toUpperCase();
    for (EmployeePayMethod m : EmployeePayMethod.values()) {
      if (m.name().equals(u)) return m.name();
    }
    throw new IllegalArgumentException("invalid_pay_method");
  }

  /** Order pay method name; blank means NONE. */
  public static String normalizeOrderPayMethod(String raw) {
    if (raw == null || raw.isBlank()) return EmployeeOrderPayMethod.NONE.name();
    String u = raw.trim().toUpperCase();
    for (EmployeeOrderPayMethod m : EmployeeOrderPayMethod.values()) {
      if (m.name().equals(u)) return m.name();
    }
    throw new IllegalArgumentException("invalid_order_pay_method");
  }

  /**
   * The order pay method in force. A NULL column (employees created before order pay existed, until the
   * startup carry-over stores it) reads as the {@link #carryOverMethod carry-over} of the service setting, so
   * those employees keep earning for orders what they earned before. New employees store NONE explicitly.
   */
  public static EmployeeOrderPayMethod orderPayMethod(EmployeeEntity emp) {
    if (emp == null) return EmployeeOrderPayMethod.NONE;
    if (emp.orderPayMethod == null || emp.orderPayMethod.isBlank()) {
      return carryOverMethod(emp.payMethod);
    }
    try {
      return EmployeeOrderPayMethod.valueOf(emp.orderPayMethod.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return EmployeeOrderPayMethod.NONE;
    }
  }

  /** The order pay rate in force (the carry-over rate while the column is NULL, see {@link #orderPayMethod}). */
  public static Double orderPayRate(EmployeeEntity emp) {
    if (emp == null) return null;
    if (emp.orderPayMethod == null || emp.orderPayMethod.isBlank()) {
      return carryOverRate(emp.payMethod, emp.payRate);
    }
    return emp.orderPayRate;
  }

  /**
   * Order pay that reproduces what an order earned before order pay existed, when every credited paid order
   * was priced with the service setting as a one-hour job ({@code rate x (1 + bonus)}, then the service
   * method's totalling rule):
   *
   * <ul>
   *   <li>PER_SERVICE (and an unset / unknown method, which the old code treated as PER_SERVICE): each order
   *       earned the rate. Becomes FIXED_PER_ORDER at that rate (exact).
   *   <li>PER_HOUR: an order counted as one hour, so it earned one hour's pay. Becomes FIXED_PER_ORDER at the
   *       hourly rate (exact).
   *   <li>PER_DAY: an order only made its day a paid day; it added nothing on a day that already had other
   *       work. Becomes NONE (no exact equivalent: a day with only orders no longer earns the day rate, where a
   *       fixed amount per order would pay the day rate for every order).
   *   <li>WEEKLY / MONTHLY: orders added nothing to the salary, which is owed once; they only counted as
   *       activity. Becomes NONE (no exact equivalent: a period with only orders no longer shows the salary).
   *   <li>PERCENT_OF_SERVICE: did not exist before; NONE.
   * </ul>
   *
   * The bonus percentage applies to order pay as it did before, so the amount per order is unchanged.
   */
  public static EmployeeOrderPayMethod carryOverMethod(String servicePayMethod) {
    String m = servicePayMethod == null ? "" : servicePayMethod.trim().toUpperCase();
    if (EmployeePayMethod.PER_DAY.name().equals(m)
        || EmployeePayMethod.WEEKLY.name().equals(m)
        || EmployeePayMethod.MONTHLY.name().equals(m)
        || EmployeePayMethod.PERCENT_OF_SERVICE.name().equals(m)) {
      return EmployeeOrderPayMethod.NONE;
    }
    return EmployeeOrderPayMethod.FIXED_PER_ORDER;
  }

  /** Rate for {@link #carryOverMethod}: the service rate (unset counts as 0, as before) or null for NONE. */
  public static Double carryOverRate(String servicePayMethod, Double servicePayRate) {
    if (carryOverMethod(servicePayMethod) == EmployeeOrderPayMethod.NONE) return null;
    return servicePayRate == null ? 0.0 : servicePayRate;
  }

  public static boolean paysForOrders(EmployeeEntity emp) {
    return orderPayMethod(emp) != EmployeeOrderPayMethod.NONE;
  }

  /**
   * Service rate for {@code method}: a rand amount >= 0, or for PERCENT_OF_SERVICE a percentage 0 to 100 with at
   * most 2 decimals. Null (not given) passes.
   */
  public static void validateServiceRate(String method, Double rate) {
    boolean percent = EmployeePayMethod.PERCENT_OF_SERVICE.name().equals(method);
    validateRate(rate, percent, "invalid_pay_rate");
  }

  /** Order rate for {@code method}; same rules as {@link #validateServiceRate}. */
  public static void validateOrderRate(String method, Double rate) {
    boolean percent = EmployeeOrderPayMethod.PERCENT_OF_ORDER.name().equals(method);
    validateRate(rate, percent, "invalid_order_pay_rate");
  }

  private static void validateRate(Double rate, boolean percent, String errorCode) {
    if (rate == null) return;
    if (rate.isNaN() || rate.isInfinite() || rate < 0) throw new IllegalArgumentException(errorCode);
    if (percent && (rate > PERCENT_MAX || !atMostTwoDecimals(rate))) {
      throw new IllegalArgumentException(errorCode);
    }
  }

  static boolean atMostTwoDecimals(double v) {
    return BigDecimal.valueOf(v).stripTrailingZeros().scale() <= 2;
  }

  /** Order total after discounts, excluding the delivery fee; never below zero. */
  public static BigDecimal orderPayBase(OrderEntity o) {
    BigDecimal total = o.totalZar == null ? BigDecimal.ZERO : o.totalZar;
    BigDecimal fee = o.deliveryFeeZar == null ? BigDecimal.ZERO : o.deliveryFeeZar;
    return total.subtract(fee).max(BigDecimal.ZERO);
  }

  /** Pay for one order under the employee's order pay setting (0 when NONE). */
  public static double orderPay(EmployeeEntity emp, OrderEntity o) {
    Double stored = orderPayRate(emp);
    double rate = stored == null ? 0.0 : stored;
    BigDecimal base =
        switch (orderPayMethod(emp)) {
          case NONE -> BigDecimal.ZERO;
          case FIXED_PER_ORDER -> BigDecimal.valueOf(rate);
          case PERCENT_OF_ORDER -> percentOf(orderPayBase(o), rate);
        };
    return withBonus(base, emp.bonusPercentage);
  }

  /**
   * Pay for one service under the employee's service pay setting. {@code hours} is the booking length (at
   * least 1) and {@code servicePrice} what the customer pays for it (null counts as 0).
   */
  public static double servicePay(EmployeeEntity emp, double hours, BigDecimal servicePrice) {
    String method = emp.payMethod == null ? "" : emp.payMethod.trim().toUpperCase();
    double rate = emp.payRate == null ? 0.0 : emp.payRate;
    BigDecimal base;
    if (EmployeePayMethod.PER_HOUR.name().equals(method)) {
      base = BigDecimal.valueOf(hours).multiply(BigDecimal.valueOf(rate));
    } else if (EmployeePayMethod.PERCENT_OF_SERVICE.name().equals(method)) {
      base = percentOf(servicePrice == null ? BigDecimal.ZERO : servicePrice, rate);
    } else {
      // PER_SERVICE (default), PER_DAY, WEEKLY, MONTHLY: the rate itself per line.
      base = BigDecimal.valueOf(rate);
    }
    return withBonus(base, emp.bonusPercentage);
  }

  /** {@code base} plus the bonus percentage, rounded half up to the cent. */
  public static double withBonus(BigDecimal base, Double bonusPercentage) {
    double bonus = bonusPercentage == null ? 0.0 : bonusPercentage;
    BigDecimal factor = HUNDRED.add(BigDecimal.valueOf(bonus)).divide(HUNDRED);
    return base.multiply(factor).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  /** Rounds a rand amount half up to the cent (0 for NaN / infinity). */
  public static double money(double v) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private static BigDecimal percentOf(BigDecimal base, double percent) {
    return base.multiply(BigDecimal.valueOf(percent)).divide(HUNDRED);
  }
}
