package com.productstore.platform.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import com.productstore.platform.entities.MerchantSubscriptionEntity;

import org.junit.jupiter.api.Test;

/**
 * UTC Instant boundary for the durable merchant free trial: valid on [{@code trialStartAt},
 * {@code trialEndAt}).
 */
class MerchantSubscriptionTrialUtcBoundaryTest {

  @Test
  void withinWindow_inclusiveStartExclusiveEnd() {
    MerchantSubscriptionEntity sub = new MerchantSubscriptionEntity();
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    Instant end = Instant.parse("2026-01-31T00:00:00Z");
    sub.trialStartAt = start;
    sub.trialEndAt = end;

    assertTrue(MerchantSubscriptionService.isWithinTrialWindow(sub, start));
    assertTrue(MerchantSubscriptionService.isWithinTrialWindow(sub, start.plusSeconds(1)));
    assertTrue(MerchantSubscriptionService.isWithinTrialWindow(sub, end.minusSeconds(1)));
    assertFalse(MerchantSubscriptionService.isWithinTrialWindow(sub, end));
    assertFalse(MerchantSubscriptionService.isWithinTrialWindow(sub, end.plusSeconds(1)));
    assertFalse(MerchantSubscriptionService.isWithinTrialWindow(sub, start.minusSeconds(1)));
  }

  @Test
  void missingDates_neverWithinWindow() {
    MerchantSubscriptionEntity sub = new MerchantSubscriptionEntity();
    Instant now = Instant.parse("2026-06-15T12:00:00Z");
    assertFalse(MerchantSubscriptionService.isWithinTrialWindow(sub, now));
    sub.trialStartAt = now;
    assertFalse(MerchantSubscriptionService.isWithinTrialWindow(sub, now));
  }

  @Test
  void thirtyDayWindow_expiresExactlyAtPlus30DaysUtc() {
    Instant created = Instant.parse("2026-03-10T15:30:00Z");
    MerchantSubscriptionEntity sub = new MerchantSubscriptionEntity();
    sub.trialStartAt = created;
    sub.trialEndAt = created.plus(MerchantSubscriptionService.TRIAL_DAYS, java.time.temporal.ChronoUnit.DAYS);

    assertTrue(MerchantSubscriptionService.isWithinTrialWindow(sub, created.plusSeconds(1)));
    assertTrue(
        MerchantSubscriptionService.isWithinTrialWindow(
            sub, sub.trialEndAt.minusSeconds(1)));
    assertFalse(MerchantSubscriptionService.isWithinTrialWindow(sub, sub.trialEndAt));
  }
}
