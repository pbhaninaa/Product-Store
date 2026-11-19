package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-11-19T10:00:00Z");

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  void allowsUpToLimitThenBlocksUntilWindowResets() {
    MutableClock clock = new MutableClock();
    RateLimiter limiter = new RateLimiter(clock, true);
    for (int i = 0; i < 3; i++) {
      assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue();
    }
    assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isFalse();
    assertThat(limiter.tryAcquire("other", 3, Duration.ofMinutes(1))).isTrue();

    clock.now = clock.now.plusSeconds(61);
    assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue();
  }

  @Test
  void exhaustedPeekDoesNotCount() {
    RateLimiter limiter = new RateLimiter(new MutableClock(), true);
    limiter.tryAcquire("k", 2, Duration.ofMinutes(1));
    assertThat(limiter.isExhausted("k", 2)).isFalse();
    limiter.tryAcquire("k", 2, Duration.ofMinutes(1));
    assertThat(limiter.isExhausted("k", 2)).isTrue();
  }

  @Test
  void disabledNeverBlocks() {
    RateLimiter limiter = new RateLimiter(new MutableClock(), false);
    for (int i = 0; i < 50; i++) {
      assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isTrue();
    }
    assertThat(limiter.isExhausted("k", 1)).isFalse();
  }

  @Test
  void rulesCoverAbusableEndpointsOnly() {
    assertThat(RateLimitFilter.match("POST", "/api/auth/login")).isNotNull();
    assertThat(RateLimitFilter.match("POST", "/api/auth/forgot-password")).isNotNull();
    assertThat(RateLimitFilter.match("POST", "/api/auth/register-client")).isNotNull();
    assertThat(RateLimitFilter.match("POST", "/api/public/m/shop/checkout/orders")).isNotNull();
    assertThat(RateLimitFilter.match("POST", "/api/public/m/shop/salon/bookings/abc/eft-proof")).isNotNull();
    assertThat(RateLimitFilter.match("GET", "/api/public/m/shop/checkout/orders/abc")).isNotNull();
    assertThat(RateLimitFilter.match("GET", "/api/public/m/shop/catalog")).isNull();
    assertThat(RateLimitFilter.match("GET", "/api/auth/login")).isNull();
  }
}
