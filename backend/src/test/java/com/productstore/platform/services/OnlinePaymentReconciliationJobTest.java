package com.productstore.platform.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.productstore.platform.config.PaystackProperties;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OnlinePaymentReconciliationJobTest {
  private final OnlinePaymentReconciliationService reconciliation = mock(OnlinePaymentReconciliationService.class);

  private PaystackProperties properties(String secret) {
    PaystackProperties p = new PaystackProperties();
    ReflectionTestUtils.setField(p, "secretKey", secret);
    return p;
  }

  @Test
  void runsWithTheConfiguredBatchSize() {
    when(reconciliation.reconcile(any(), anyInt()))
        .thenReturn(new OnlinePaymentReconciliationService.Summary(0, 0, 0, 0, 0, 0));
    new OnlinePaymentReconciliationJob(reconciliation, properties("sk_test_x"), true, 7).run();
    verify(reconciliation).reconcile(any(), eq(7));
  }

  @Test
  void doesNothingWhenDisabledOrWithoutASecretKey() {
    new OnlinePaymentReconciliationJob(reconciliation, properties("sk_test_x"), false, 50).run();
    new OnlinePaymentReconciliationJob(reconciliation, properties(""), true, 50).run();
    verify(reconciliation, never()).reconcile(any(), anyInt());
  }

  @Test
  void neverRunsTwiceAtTheSameTime() throws Exception {
    CountDownLatch inside = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(reconciliation.reconcile(any(), anyInt()))
        .thenAnswer(
            inv -> {
              inside.countDown();
              release.await(5, TimeUnit.SECONDS);
              return new OnlinePaymentReconciliationService.Summary(0, 0, 0, 0, 0, 0);
            });
    OnlinePaymentReconciliationJob job =
        new OnlinePaymentReconciliationJob(reconciliation, properties("sk_test_x"), true, 50);

    Thread first = new Thread(job::run);
    first.start();
    inside.await(5, TimeUnit.SECONDS);
    job.run(); // overlaps the first run: skipped
    release.countDown();
    first.join(5000);

    verify(reconciliation, times(1)).reconcile(any(), anyInt());
  }
}
