package com.productstore.platform.constants;

/**
 * How an employee is paid for orders credited to them ({@code orders.completed_by_employee_id}). New employees
 * default to {@link #NONE}. A null stored value (employees created before this setting existed, until the
 * startup carry-over stores it) reads as the carry-over of their service pay, see {@code
 * EmployeePayRules#carryOverMethod}.
 */
public enum EmployeeOrderPayMethod {
  /** Orders earn nothing. */
  NONE,
  /** Fixed rand amount per completed order. */
  FIXED_PER_ORDER,
  /** Percentage (0 to 100) of the order total after discounts, excluding the delivery fee. */
  PERCENT_OF_ORDER
}
