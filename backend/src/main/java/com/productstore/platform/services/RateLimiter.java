package com.productstore.platform.services;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.productstore.platform.config.EnvFlags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counters kept in memory. Good enough for a single backend instance; if the API is ever
 * scaled to several replicas, move the counters to a shared store (each replica would allow the limit).
 */
@Component
public class RateLimiter {
  private static final int PURGE_EVERY = 1000;

  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final AtomicLong calls = new AtomicLong();
  private final Clock clock;
  private final boolean enabled;

  @Autowired
  public RateLimiter(@Value("${app.rate-limit.enabled:true}") String enabled) {
    this(Clock.systemUTC(), EnvFlags.parse(enabled, true));
  }

  RateLimiter(Clock clock, boolean enabled) {
    this.clock = clock;
    this.enabled = enabled;
  }

  /** Counts one hit against {@code key}; false once more than {@code limit} hits land in the window. */
  public boolean tryAcquire(String key, int limit, Duration window) {
    if (!enabled) return true;
    long now = clock.millis();
    maybePurge(now);
    Window w =
        windows.compute(
            key,
            (k, cur) -> {
              if (cur == null || now >= cur.resetAt) return new Window(now + window.toMillis(), 1);
              cur.count++;
              return cur;
            });
    return w.count <= limit;
  }

  /** True while {@code key} has already used up {@code limit} hits in its current window (does not count). */
  public boolean isExhausted(String key, int limit) {
    if (!enabled) return false;
    Window w = windows.get(key);
    return w != null && clock.millis() < w.resetAt && w.count >= limit;
  }

  /** Seconds until {@code key}'s window resets (for Retry-After). */
  public long retryAfterSeconds(String key) {
    Window w = windows.get(key);
    if (w == null) return 1;
    return Math.max(1, (w.resetAt - clock.millis() + 999) / 1000);
  }

  private void maybePurge(long now) {
    if (calls.incrementAndGet() % PURGE_EVERY != 0) return;
    windows.entrySet().removeIf(e -> now >= e.getValue().resetAt);
  }

  private static final class Window {
    final long resetAt;
    int count;

    Window(long resetAt, int count) {
      this.resetAt = resetAt;
      this.count = count;
    }
  }

  /** Thrown when a caller is over a limit; mapped to HTTP 429. */
  public static final class RateLimitedException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
      super("rate_limited");
      this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
      return retryAfterSeconds;
    }
  }
}
