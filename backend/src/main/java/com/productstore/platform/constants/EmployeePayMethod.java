package com.productstore.platform.constants;

/**
 * How an employee is paid for services (salon bookings). Orders have their own setting, {@link
 * EmployeeOrderPayMethod}.
 */
public enum EmployeePayMethod {
    /** Fixed rand amount per service. */
    PER_SERVICE,
    PER_HOUR,
    PER_DAY,
    WEEKLY,
    MONTHLY,
    /** Percentage (0 to 100) of the service price the customer pays, after discounts. */
    PERCENT_OF_SERVICE
}
