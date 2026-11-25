package com.productstore.platform.services;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every few minutes, cancels orders that were never paid so they stop reserving stock and promotion uses.
 * Without this, a script placing unpaid orders could make every product show "out of stock" indefinitely.
 * The same run cancels pay-in-store salon bookings whose booking fee was not paid in time, which frees their slot
 * (booking fee FR-5 / BR-3: the same 2 hours as unpaid in-app orders unless configured otherwise).
 */
@Component
public class UnpaidOrderExpiryJob {
  private static final Logger log = LoggerFactory.getLogger(UnpaidOrderExpiryJob.class);

  private final CheckoutService checkout;
  private final SalonBookingService salonBookings;
  private final boolean enabled;
  private final Duration inAppTtl;
  private final Duration eftTtl;
  private final Duration cashTtl;
  private final Duration bookingFeeTtl;
  private final Clock clock = Clock.systemUTC();

  public UnpaidOrderExpiryJob(
      CheckoutService checkout,
      SalonBookingService salonBookings,
      @Value("${app.orders.unpaid-expiry.enabled:true}") boolean enabled,
      @Value("${app.orders.unpaid-expiry.in-app:PT2H}") Duration inAppTtl,
      @Value("${app.orders.unpaid-expiry.eft-awaiting-proof:PT48H}") Duration eftTtl,
      @Value("${app.orders.unpaid-expiry.cash:PT72H}") Duration cashTtl,
      @Value("${app.bookings.unpaid-fee-expiry:${app.orders.unpaid-expiry.in-app:PT2H}}") Duration bookingFeeTtl) {
    this.checkout = checkout;
    this.salonBookings = salonBookings;
    this.enabled = enabled;
    this.inAppTtl = inAppTtl;
    this.eftTtl = eftTtl;
    this.cashTtl = cashTtl;
    this.bookingFeeTtl = bookingFeeTtl;
  }

  @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
  public void run() {
    if (!enabled) return;
    try {
      int n = checkout.expireStaleUnpaidOrders(clock.instant(), inAppTtl, eftTtl, cashTtl);
      if (n > 0) log.info("Expired {} unpaid order(s)", n);
    } catch (RuntimeException e) {
      log.warn("Unpaid order expiry failed: {}", e.toString());
    }
    try {
      int n = salonBookings.expireUnpaidBookingFees(clock.instant(), bookingFeeTtl);
      if (n > 0) log.info("Cancelled {} booking(s) whose booking fee was not paid", n);
    } catch (RuntimeException e) {
      log.warn("Unpaid booking fee expiry failed: {}", e.toString());
    }
  }
}
