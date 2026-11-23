package com.productstore.platform.services;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;

import com.productstore.platform.config.PaystackProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every {@code app.payments.reconcile-interval} (default 2 minutes) confirms waiting online payments with
 * Paystack (see {@link OnlinePaymentReconciliationService}). Off with {@code app.payments.reconcile-enabled=false}
 * and while no Paystack secret key is set.
 *
 * <p>Within one app instance runs never overlap (fixed delay plus a guard). Several instances may each run
 * it; that only costs extra verify calls, because settling locks the record row and is idempotent.
 */
@Component
public class OnlinePaymentReconciliationJob {
  private static final Logger log = LoggerFactory.getLogger(OnlinePaymentReconciliationJob.class);

  private final OnlinePaymentReconciliationService reconciliation;
  private final PaystackProperties paystack;
  private final boolean enabled;
  private final int batchSize;
  private final Clock clock = Clock.systemUTC();
  private final AtomicBoolean running = new AtomicBoolean(false);

  public OnlinePaymentReconciliationJob(
      OnlinePaymentReconciliationService reconciliation,
      PaystackProperties paystack,
      @Value("${app.payments.reconcile-enabled:true}") boolean enabled,
      @Value("${app.payments.reconcile-batch-size:50}") int batchSize) {
    this.reconciliation = reconciliation;
    this.paystack = paystack;
    this.enabled = enabled;
    this.batchSize = batchSize;
  }

  @Scheduled(
      initialDelayString = "${app.payments.reconcile-initial-delay:PT1M}",
      fixedDelayString = "${app.payments.reconcile-interval:PT2M}")
  public void run() {
    if (!enabled || !paystack.hasSecretKey()) return;
    if (!running.compareAndSet(false, true)) return;
    try {
      var s = reconciliation.reconcile(clock.instant(), batchSize);
      log.info(
          "Payment reconciliation: checked={} settled={} alreadySettled={} notPaid={} pending={} rejected={}"
              + " duplicate={} needsRefund={} errors={} closed={}",
          s.checked(), s.settled(), s.alreadySettled(), s.notPaid(), s.pending(), s.rejected(),
          s.duplicate(), s.needsRefund(), s.errors(), s.closed());
    } catch (RuntimeException e) {
      log.warn("Payment reconciliation failed: {}", e.toString());
    } finally {
      running.set(false);
    }
  }
}
