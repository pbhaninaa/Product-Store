package com.productstore.platform.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;

/**
 * Checks the 6-digit handover code of an order or booking (see {@link HandoverCodes}), and stops guessing: after
 * {@link #MAX_FAILURES} wrong codes for the same order/booking, further tries are refused (HTTP 429) for
 * {@link #WINDOW}. Without this, someone at the till could try codes until one marks an unpaid order paid or
 * releases goods. Cash confirmation, order handover and service start share one failure count per order/booking.
 * Always on (independent of the request rate limits) and kept in memory, so it covers one instance.
 */
final class CashCodeGuard {
  static final int MAX_FAILURES = 5;
  static final Duration WINDOW = Duration.ofMinutes(30);

  private static final RateLimiter FAILURES = new RateLimiter(Clock.systemUTC(), true);

  private CashCodeGuard() {}

  /**
   * @param key e.g. {@code "order:<id>"}; failures are counted per key
   * @return whether {@code provided} matches {@code stored}
   * @throws RateLimiter.RateLimitedException once the key has used up its wrong guesses
   */
  static boolean check(String key, String stored, String provided) {
    if (FAILURES.isExhausted(key, MAX_FAILURES)) {
      throw new RateLimiter.RateLimitedException(FAILURES.retryAfterSeconds(key));
    }
    boolean ok = matches(stored, provided);
    if (!ok) FAILURES.tryAcquire(key, MAX_FAILURES, WINDOW);
    return ok;
  }

  static boolean matches(String stored, String provided) {
    String a = normalize(stored);
    String b = normalize(provided);
    if (a.isEmpty() || b.isEmpty()) return false;
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  private static String normalize(String s) {
    return s == null ? "" : s.trim().replaceAll("\s+", "");
  }
}
