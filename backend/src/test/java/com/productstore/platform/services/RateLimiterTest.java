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

  /** Percent-encoding a path character must not dodge a limit (SEC-3). */
  @Test
  void encodedPathsAreMatchedOnTheDecodedPath() {
    var statusReq = new org.springframework.mock.web.MockHttpServletRequest(
        "GET", "/api/public/m/shop/checkout/orders/abc/payment%2Dstatus");
    assertThat(RateLimitFilter.pathOf(statusReq)).isEqualTo("/api/public/m/shop/checkout/orders/abc/payment-status");
    assertThat(RateLimitFilter.match("GET", RateLimitFilter.pathOf(statusReq))).isNotNull();

    var loginReq = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/auth/log%69n");
    assertThat(RateLimitFilter.match("POST", RateLimitFilter.pathOf(loginReq))).isNotNull();

    var semicolonReq = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/auth/login;x=1");
    assertThat(RateLimitFilter.match("POST", RateLimitFilter.pathOf(semicolonReq))).isNotNull();

    var doubleSlashReq = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api//auth/login");
    assertThat(RateLimitFilter.match("POST", RateLimitFilter.pathOf(doubleSlashReq))).isNotNull();
  }

  @Test
  void encodedLoginIsActuallyLimitedByTheFilter() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(new RateLimiter("true"));
    int last = 0;
    for (int i = 0; i < 25; i++) {
      var req = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/auth/log%69n");
      req.setRemoteAddr("10.9.8.7");
      var res = new org.springframework.mock.web.MockHttpServletResponse();
      filter.doFilter(req, res, new org.springframework.mock.web.MockFilterChain());
      last = res.getStatus();
    }
    assertThat(last).isEqualTo(429);
  }

  @Test
  void rulesCoverAbusableEndpointsOnly() {
    assertThat(RateLimitFilter.match("POST", "/api/auth/login")).isNotNull();
    assertThat(RateLimitFilter.match("POST", "/api/auth/forgot-password")).isNotNull();
    assertThat(RateLimitFilter.match("POST", "/api/auth/register-client")).isNotNull();
    assertThat(RateLimitFilter.match("POST", "/api/public/m/shop/checkout/orders")).isNotNull();
    assertThat(RateLimitFilter.match("POST", "/api/public/m/shop/salon/bookings/abc/eft-proof")).isNotNull();
    assertThat(RateLimitFilter.match("GET", "/api/public/m/shop/checkout/orders/abc")).isNotNull();
    // Payment status checks may call Paystack.
    assertThat(RateLimitFilter.match("GET", "/api/public/m/shop/checkout/orders/abc/payment-status")).isNotNull();
    assertThat(RateLimitFilter.match("GET", "/api/public/m/shop/salon/bookings/abc/payment-status")).isNotNull();
    assertThat(RateLimitFilter.match("GET", "/api/m/shop/admin/subscription/online-payments/abc/status")).isNotNull();
    assertThat(RateLimitFilter.match("GET", "/api/m/shop/admin/subscription/me")).isNull();
    assertThat(RateLimitFilter.match("GET", "/api/public/m/shop/catalog")).isNull();
    assertThat(RateLimitFilter.match("GET", "/api/auth/login")).isNull();
  }
}
