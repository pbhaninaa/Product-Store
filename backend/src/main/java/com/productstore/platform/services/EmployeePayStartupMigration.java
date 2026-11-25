package com.productstore.platform.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-off, idempotent employee pay migrations for the order / service pay split, run at startup in this
 * order:
 *
 * <ol>
 *   <li>{@link EmployeeService#carryOverOrderPay()}: employees from before order pay existed get the order pay
 *       that reproduces what their orders earned (until then a NULL setting already reads that way, so no
 *       calculation in between can see anything else);
 *   <li>{@link EmployeeService#snapshotLegacyMarks()}: payroll marks from before paid amounts were stored get
 *       the amount the old rules showed, so later setting changes never move past payouts.
 * </ol>
 *
 * Each step is its own transaction; a failure is logged and never blocks startup (the next start retries).
 */
@Component
@Order(40)
public class EmployeePayStartupMigration implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(EmployeePayStartupMigration.class);

  private final EmployeeService employeeService;

  public EmployeePayStartupMigration(EmployeeService employeeService) {
    this.employeeService = employeeService;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      int carried = employeeService.carryOverOrderPay();
      if (carried > 0) log.info("Order pay: carried over the previous order pay for {} employee(s)", carried);
    } catch (RuntimeException e) {
      log.warn("Order pay: could not carry over the previous order pay: {}", e.getMessage());
    }
    try {
      int filled = employeeService.snapshotLegacyMarks();
      if (filled > 0) log.info("Payroll marks: stored the paid amount on {} older mark(s)", filled);
    } catch (RuntimeException e) {
      log.warn("Payroll marks: could not store paid amounts on older marks: {}", e.getMessage());
    }
  }
}
