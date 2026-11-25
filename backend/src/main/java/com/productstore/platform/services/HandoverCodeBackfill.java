package com.productstore.platform.services;

import java.util.UUID;

import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Gives a handover code to every order and booking still in flight that was placed before all of them got one
 * (until 2026-11 only pay-in-store rows had a code). Without it, staff could not hand those orders over or start
 * those services. Orders already handed over or cancelled and bookings already started, completed or cancelled
 * are left alone.
 *
 * <p>Runs at every start (see {@code db/migration/V15__handover_codes.sql}, reference only: Flyway is inactive)
 * and is idempotent: each row is only updated while it still has no code, so a second start or a second instance
 * changes nothing. Codes come from {@link HandoverCodes#generate()} (SecureRandom), which plain SQL cannot match.
 * A failure is logged and startup continues; the next start retries. Logs counts only, never codes.
 */
@Component
public class HandoverCodeBackfill implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(HandoverCodeBackfill.class);

  private final OrderRepository orders;
  private final SalonBookingRepository bookings;

  public HandoverCodeBackfill(OrderRepository orders, SalonBookingRepository bookings) {
    this.orders = orders;
    this.bookings = bookings;
  }

  public record Result(int orders, int bookings) {}

  @Override
  public void run(ApplicationArguments args) {
    try {
      Result r = backfill();
      if (r.orders() + r.bookings() > 0) {
        log.info("Handover codes issued to {} order(s) and {} booking(s)", r.orders(), r.bookings());
      }
    } catch (RuntimeException e) {
      log.warn("Handover code backfill failed, will retry on next start: {}", e.getMessage());
    }
  }

  public Result backfill() {
    int o = 0;
    for (UUID id : orders.findIdsAwaitingHandoverWithoutCode()) {
      o += orders.assignHandoverCodeIfMissing(id, HandoverCodes.generate());
    }
    int b = 0;
    for (UUID id : bookings.findIdsAwaitingStartWithoutHandoverCode()) {
      b += bookings.assignHandoverCodeIfMissing(id, HandoverCodes.generate());
    }
    return new Result(o, b);
  }
}
