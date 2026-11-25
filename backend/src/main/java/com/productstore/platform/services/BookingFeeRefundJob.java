package com.productstore.platform.services;

import java.util.concurrent.atomic.AtomicBoolean;

import com.productstore.platform.config.PaystackProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries booking fee refunds that Paystack has not accepted yet (edge case 5), every {@code
 * app.booking-fee.refund-retry-interval} (default 5 minutes). Each refund has its own backoff (see {@link
 * BookingFeeRefundService}). Off with {@code app.booking-fee.refund-retry-enabled=false} and while no Paystack
 * secret key is set. Runs never overlap within one instance; several instances are safe because sending claims the
 * refund row first.
 */
@Component
public class BookingFeeRefundJob {
  private static final Logger log = LoggerFactory.getLogger(BookingFeeRefundJob.class);
  static final int BATCH = 50;

  private final BookingFeeRefundService refunds;
  private final PaystackProperties paystack;
  private final boolean enabled;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public BookingFeeRefundJob(
      BookingFeeRefundService refunds,
      PaystackProperties paystack,
      @Value("${app.booking-fee.refund-retry-enabled:true}") boolean enabled) {
    this.refunds = refunds;
    this.paystack = paystack;
    this.enabled = enabled;
  }

  @Scheduled(
      initialDelayString = "PT2M",
      fixedDelayString = "${app.booking-fee.refund-retry-interval:PT5M}")
  public void run() {
    if (!enabled || !paystack.hasSecretKey()) return;
    if (!running.compareAndSet(false, true)) return;
    try {
      int done = refunds.sendDue(BATCH);
      if (done > 0) log.info("Booking fee refund retry: {} refund(s) accepted by Paystack", done);
    } catch (RuntimeException e) {
      log.warn("Booking fee refund retry failed: {}", e.toString());
    } finally {
      running.set(false);
    }
  }
}
