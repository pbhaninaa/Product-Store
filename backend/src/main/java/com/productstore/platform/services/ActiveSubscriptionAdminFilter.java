package com.productstore.platform.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.productstore.platform.repositories.TenantRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Blocks merchant admin APIs when the store has no active/valid subscription period. Plan & billing
 * endpoints under {@code /admin/subscription/**} remain available so owners can activate.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class ActiveSubscriptionAdminFilter extends OncePerRequestFilter {
  private static final Pattern ADMIN_PATH =
      Pattern.compile("^/api/m/([^/]+)/admin(?:/.*)?$");
  private static final Pattern SUBSCRIPTION_PATH =
      Pattern.compile("^/api/m/[^/]+/admin/subscription(?:/.*)?$");
  private static final Pattern HELP_PATH =
      Pattern.compile("^/api/m/[^/]+/admin/help(?:/.*)?$");

  private final TenantRepository tenants;
  private final MerchantSubscriptionService subscriptions;

  public ActiveSubscriptionAdminFilter(
      TenantRepository tenants, MerchantSubscriptionService subscriptions) {
    this.tenants = tenants;
    this.subscriptions = subscriptions;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getServletPath();
    if (path == null || path.isBlank()) {
      path = request.getRequestURI() == null ? "" : request.getRequestURI();
    }
    Matcher admin = ADMIN_PATH.matcher(path);
    if (!admin.matches()
        || SUBSCRIPTION_PATH.matcher(path).matches()
        || HELP_PATH.matcher(path).matches()) {
      filterChain.doFilter(request, response);
      return;
    }

    String slug = admin.group(1);
    var tenant = tenants.findBySlug(slug).orElse(null);
    if (tenant == null) {
      filterChain.doFilter(request, response);
      return;
    }

    if (!subscriptions.hasEffectiveSubscription(tenant.id)) {
      response.setStatus(HttpServletResponse.SC_CONFLICT);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"error\":\"subscription_inactive\"}");
      return;
    }

    filterChain.doFilter(request, response);
  }
}
