package com.productstore.platform.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Per-IP limits on the public endpoints that can be abused anonymously: login/signup brute force,
 * password-reset email floods, scripted orders that lock up stock, and reference guessing on track.
 *
 * <p>The client IP is {@code request.getRemoteAddr()}, which {@code server.forward-headers-strategy=framework}
 * rewrites from the proxy's X-Forwarded-For on Railway.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  record Rule(String name, String method, Pattern path, int limit, Duration window) {}

  static final List<Rule> RULES =
      List.of(
          new Rule("login", "POST", Pattern.compile("^/api/auth/login$"), 20, Duration.ofMinutes(15)),
          new Rule("forgot", "POST", Pattern.compile("^/api/auth/forgot-password$"), 5, Duration.ofMinutes(15)),
          new Rule("reset", "POST", Pattern.compile("^/api/auth/reset-password$"), 10, Duration.ofMinutes(15)),
          new Rule("register", "POST", Pattern.compile("^/api/auth/register-[a-z-]+$"), 10, Duration.ofHours(1)),
          new Rule(
              "order",
              "POST",
              Pattern.compile("^/api/public/m/[^/]+/(checkout/orders|salon/bookings)$"),
              15,
              Duration.ofMinutes(10)),
          new Rule(
              "proof",
              "POST",
              Pattern.compile("^/api/public/m/[^/]+/(checkout/orders|salon/bookings)/[^/]+/eft-proof$"),
              10,
              Duration.ofMinutes(10)),
          new Rule(
              "track",
              "GET",
              Pattern.compile("^/api/public/m/[^/]+/(checkout/orders|salon/bookings)/[^/]+$"),
              30,
              Duration.ofMinutes(1)),
          // A pending online payment status check calls Paystack, so it is limited like tracking.
          new Rule(
              "payment-status",
              "GET",
              Pattern.compile("^/api/public/m/[^/]+/(checkout/orders|salon/bookings)/[^/]+/payment-status$"),
              30,
              Duration.ofMinutes(1)),
          new Rule(
              "subscription-payment-status",
              "GET",
              Pattern.compile("^/api/m/[^/]+/admin/subscription/online-payments/[^/]+/status$"),
              30,
              Duration.ofMinutes(1)));

  private final RateLimiter limiter;

  public RateLimitFilter(RateLimiter limiter) {
    this.limiter = limiter;
  }

  private static final UrlPathHelper PATHS = decodingPathHelper();

  private static UrlPathHelper decodingPathHelper() {
    UrlPathHelper helper = new UrlPathHelper();
    helper.setUrlDecode(true);
    helper.setRemoveSemicolonContent(true);
    return helper;
  }

  /**
   * The path the application routes on: percent-decoded, without the context path and {@code ;} parameters,
   * with repeated slashes collapsed. Matching the raw URI would let {@code payment%2Dstatus} slip past a rule.
   */
  static String pathOf(HttpServletRequest request) {
    String path = PATHS.getPathWithinApplication(request);
    return path == null ? "" : path.replaceAll("/{2,}", "/");
  }

  static Rule match(String method, String path) {
    for (Rule r : RULES) {
      if (r.method().equalsIgnoreCase(method) && r.path().matcher(path).matches()) return r;
    }
    return null;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Rule rule = match(request.getMethod(), pathOf(request));
    if (rule != null) {
      String key = "ip:" + rule.name() + ":" + request.getRemoteAddr();
      if (!limiter.tryAcquire(key, rule.limit(), rule.window())) {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(limiter.retryAfterSeconds(key)));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"rate_limited\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
