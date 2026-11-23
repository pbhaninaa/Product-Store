package com.productstore.platform.services;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.productstore.platform.config.PaystackProperties;

import org.springframework.stereotype.Component;

/**
 * Paystack "verify transaction" with a short in-memory cache per reference, so a polling return page, many
 * open tabs or the reconciliation job cannot turn into a flood of calls to Paystack. Answers (including
 * "not found") are reused for {@code paystack.verify-cache-ttl} (default 10 seconds); errors are never cached.
 * The cache is bounded: past {@link #MAX_ENTRIES} it drops expired entries, and if still full, everything.
 */
@Component
public class PaystackVerifier {
  static final int MAX_ENTRIES = 2000;

  private record Entry(long atNanos, Optional<PaystackClient.Transaction> result) {}

  private final PaystackClient paystack;
  private final PaystackProperties properties;
  private final Map<String, Entry> cache = new ConcurrentHashMap<>();

  public PaystackVerifier(PaystackClient paystack, PaystackProperties properties) {
    this.paystack = paystack;
    this.properties = properties;
  }

  /**
   * @return the transaction, or empty when Paystack does not know the reference
   * @throws PaystackClient.PaystackException when Paystack cannot be asked
   */
  public Optional<PaystackClient.Transaction> verify(String reference) {
    Duration ttl = properties.getVerifyCacheTtl();
    long now = System.nanoTime();
    if (!ttl.isZero()) {
      Entry hit = cache.get(reference);
      if (hit != null && now - hit.atNanos() < ttl.toNanos()) {
        return hit.result();
      }
    }
    Optional<PaystackClient.Transaction> result = paystack.verify(reference);
    if (!ttl.isZero()) {
      if (cache.size() >= MAX_ENTRIES) {
        cache.entrySet().removeIf(e -> now - e.getValue().atNanos() >= ttl.toNanos());
        if (cache.size() >= MAX_ENTRIES) cache.clear();
      }
      cache.put(reference, new Entry(now, result));
    }
    return result;
  }

  int size() {
    return cache.size();
  }
}
