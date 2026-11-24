package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigDecimal;

import com.productstore.platform.constants.EmployeeOrderPayMethod;
import com.productstore.platform.entities.EmployeeEntity;
import com.productstore.platform.entities.OrderEntity;

import org.junit.jupiter.api.Test;

class EmployeePayRulesTest {

  private static EmployeeEntity emp(String orderMethod, Double orderRate, String payMethod, Double payRate) {
    EmployeeEntity e = new EmployeeEntity();
    e.orderPayMethod = orderMethod;
    e.orderPayRate = orderRate;
    e.payMethod = payMethod;
    e.payRate = payRate;
    return e;
  }

  private static OrderEntity order(String total, String deliveryFee) {
    OrderEntity o = new OrderEntity();
    o.totalZar = new BigDecimal(total);
    o.deliveryFeeZar = new BigDecimal(deliveryFee);
    return o;
  }

  @Test
  void newEmployeesDefaultToNoOrderPay() {
    // What createEmployee stores when no order pay is given.
    assertThat(EmployeePayRules.normalizeOrderPayMethod(null)).isEqualTo("NONE");
    assertThat(EmployeePayRules.normalizeOrderPayMethod(" ")).isEqualTo("NONE");
    EmployeeEntity stored = emp("NONE", null, "PER_SERVICE", 100.0);
    assertThat(EmployeePayRules.paysForOrders(stored)).isFalse();
    assertThat(EmployeePayRules.orderPay(stored, order("200.00", "0"))).isZero();
    assertThat(EmployeePayRules.normalizeOrderPayMethod("percent_of_order")).isEqualTo("PERCENT_OF_ORDER");
    assertThatThrownBy(() -> EmployeePayRules.normalizeOrderPayMethod("PER_HOUR"))
        .hasMessage("invalid_order_pay_method");
  }

  @Test
  void unsetOrderPayCarriesOverWhatOrdersEarnedBefore() {
    // Old rule: every credited order = service rate x (1 + bonus), as a one-hour job.
    EmployeeEntity perService = emp(null, null, "PER_SERVICE", 150.0);
    assertThat(EmployeePayRules.orderPayMethod(perService)).isEqualTo(EmployeeOrderPayMethod.FIXED_PER_ORDER);
    assertThat(EmployeePayRules.orderPayRate(perService)).isEqualTo(150.0);
    assertThat(EmployeePayRules.orderPay(perService, order("999.00", "40.00"))).isEqualTo(150.0);

    EmployeeEntity hourly = emp(null, null, "PER_HOUR", 80.0);
    hourly.bonusPercentage = 10.0;
    assertThat(EmployeePayRules.orderPayMethod(hourly)).isEqualTo(EmployeeOrderPayMethod.FIXED_PER_ORDER);
    assertThat(EmployeePayRules.orderPay(hourly, order("50.00", "0"))).isEqualTo(88.0); // one hour + bonus

    // The old code treated an unset or unknown method as PER_SERVICE; an unset rate as 0.
    assertThat(EmployeePayRules.orderPayMethod(emp(null, null, null, 60.0)))
        .isEqualTo(EmployeeOrderPayMethod.FIXED_PER_ORDER);
    assertThat(EmployeePayRules.orderPayRate(emp(null, null, "", 60.0))).isEqualTo(60.0);
    assertThat(EmployeePayRules.orderPayRate(emp(null, null, "PER_SERVICE", null))).isEqualTo(0.0);

    // Orders added nothing to a day rate or salary already earned: no order pay.
    for (String m : new String[] {"PER_DAY", "WEEKLY", "MONTHLY", "PERCENT_OF_SERVICE"}) {
      EmployeeEntity e = emp(null, null, m, 500.0);
      assertThat(EmployeePayRules.orderPayMethod(e)).as(m).isEqualTo(EmployeeOrderPayMethod.NONE);
      assertThat(EmployeePayRules.orderPayRate(e)).as(m).isNull();
      assertThat(EmployeePayRules.paysForOrders(e)).as(m).isFalse();
    }

    // A stored setting always wins over the carry-over.
    assertThat(EmployeePayRules.orderPayMethod(emp("NONE", null, "PER_SERVICE", 150.0)))
        .isEqualTo(EmployeeOrderPayMethod.NONE);
    assertThat(EmployeePayRules.orderPayRate(emp("PERCENT_OF_ORDER", 5.0, "PER_SERVICE", 150.0))).isEqualTo(5.0);
  }

  @Test
  void orderBaseIsTotalAfterDiscountsLessDeliveryFee() {
    // subtotal 220 - discount 20 + delivery 30 = total 230 -> base 200
    assertThat(EmployeePayRules.orderPayBase(order("230.00", "30.00"))).isEqualByComparingTo("200.00");
    // never negative
    assertThat(EmployeePayRules.orderPayBase(order("0.00", "30.00"))).isEqualByComparingTo("0");
  }

  @Test
  void percentOfOrderExcludesDeliveryAndRoundsHalfUp() {
    assertThat(EmployeePayRules.orderPay(emp("PERCENT_OF_ORDER", 12.5, null, null), order("230.00", "30.00")))
        .isEqualTo(25.0);
    // 50% of 10.05 = 5.025 -> 5.03 (half up; binary double maths would give 5.02)
    assertThat(EmployeePayRules.orderPay(emp("PERCENT_OF_ORDER", 50.0, null, null), order("10.05", "0")))
        .isEqualTo(5.03);
    assertThat(EmployeePayRules.orderPay(emp("FIXED_PER_ORDER", 35.5, null, null), order("999.00", "50.00")))
        .isEqualTo(35.5);
  }

  @Test
  void bonusAppliesOnTopOfOrderAndServicePay() {
    EmployeeEntity e = emp("FIXED_PER_ORDER", 100.0, "PERCENT_OF_SERVICE", 40.0);
    e.bonusPercentage = 10.0;
    assertThat(EmployeePayRules.orderPay(e, order("50.00", "0"))).isEqualTo(110.0);
    assertThat(EmployeePayRules.servicePay(e, 1.0, new BigDecimal("150.00"))).isEqualTo(66.0);
  }

  @Test
  void percentOfServiceUsesThePriceAndOtherMethodsIgnoreIt() {
    assertThat(EmployeePayRules.servicePay(emp(null, null, "PERCENT_OF_SERVICE", 40.0), 1.0, new BigDecimal("150.00")))
        .isEqualTo(60.0);
    assertThat(EmployeePayRules.servicePay(emp(null, null, "PERCENT_OF_SERVICE", 40.0), 1.0, null)).isZero();
    assertThat(EmployeePayRules.servicePay(emp(null, null, "PER_SERVICE", 100.0), 1.0, new BigDecimal("150.00")))
        .isEqualTo(100.0);
    assertThat(EmployeePayRules.servicePay(emp(null, null, "PER_HOUR", 80.0), 1.5, new BigDecimal("150.00")))
        .isEqualTo(120.0);
  }

  @Test
  void percentagesMustBeZeroToHundredWithTwoDecimals() {
    assertThatNoException().isThrownBy(() -> EmployeePayRules.validateOrderRate("PERCENT_OF_ORDER", 0.0));
    assertThatNoException().isThrownBy(() -> EmployeePayRules.validateOrderRate("PERCENT_OF_ORDER", 100.0));
    assertThatNoException().isThrownBy(() -> EmployeePayRules.validateOrderRate("PERCENT_OF_ORDER", 12.34));
    assertThatThrownBy(() -> EmployeePayRules.validateOrderRate("PERCENT_OF_ORDER", 100.01))
        .hasMessage("invalid_order_pay_rate");
    assertThatThrownBy(() -> EmployeePayRules.validateOrderRate("PERCENT_OF_ORDER", 12.345))
        .hasMessage("invalid_order_pay_rate");
    assertThatThrownBy(() -> EmployeePayRules.validateOrderRate("PERCENT_OF_ORDER", -0.01))
        .hasMessage("invalid_order_pay_rate");
    assertThatThrownBy(() -> EmployeePayRules.validateServiceRate("PERCENT_OF_SERVICE", 150.0))
        .hasMessage("invalid_pay_rate");
    assertThatNoException().isThrownBy(() -> EmployeePayRules.validateServiceRate("PERCENT_OF_SERVICE", 99.99));
  }

  @Test
  void fixedAmountsMustNotBeNegative() {
    assertThatNoException().isThrownBy(() -> EmployeePayRules.validateOrderRate("FIXED_PER_ORDER", 0.0));
    assertThatNoException().isThrownBy(() -> EmployeePayRules.validateOrderRate("FIXED_PER_ORDER", 1500.0));
    assertThatThrownBy(() -> EmployeePayRules.validateOrderRate("FIXED_PER_ORDER", -1.0))
        .hasMessage("invalid_order_pay_rate");
    assertThatThrownBy(() -> EmployeePayRules.validateServiceRate("PER_SERVICE", -5.0))
        .hasMessage("invalid_pay_rate");
    assertThatThrownBy(() -> EmployeePayRules.validateServiceRate("PER_SERVICE", Double.NaN))
        .hasMessage("invalid_pay_rate");
    assertThatNoException().isThrownBy(() -> EmployeePayRules.validateServiceRate("PER_SERVICE", null));
  }

  @Test
  void moneyRoundsHalfUpToTheCent() {
    assertThat(EmployeePayRules.money(1.005)).isEqualTo(1.01);
    assertThat(EmployeePayRules.money(2.675)).isEqualTo(2.68);
    assertThat(EmployeePayRules.money(Double.NaN)).isZero();
  }
}
