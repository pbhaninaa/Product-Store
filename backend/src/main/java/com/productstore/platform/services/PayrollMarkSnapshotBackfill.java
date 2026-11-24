package com.productstore.platform.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * At startup, stores the paid amount on payroll marks made before amounts were snapshotted, so the
 * separate order / service pay rules never change what was already paid out (see {@link
 * EmployeeService#snapshotLegacyMarks()}). Idempotent; a failure is logged and never blocks startup.
 */
@Component
@Order(40)
public class PayrollMarkSnapshotBackfill implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(PayrollMarkSnapshotBackfill.class);

  private final EmployeeService employeeService;

  public PayrollMarkSnapshotBackfill(EmployeeService employeeService) {
    this.employeeService = employeeService;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      int filled = employeeService.snapshotLegacyMarks();
      if (filled > 0) log.info("Payroll marks: stored the paid amount on {} older mark(s)", filled);
    } catch (RuntimeException e) {
      log.warn("Payroll marks: could not store paid amounts on older marks: {}", e.getMessage());
    }
  }
}
