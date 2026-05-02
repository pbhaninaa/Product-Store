package com.productstore.platform.services.multitenancy;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

public final class PathBasedTenantResolver implements TenantResolver {
  private static final Pattern MERCHANT_SLUG =
      Pattern.compile("^/api/(public/)?m/([^/]+)/.*$");

  @Override
  public Optional<String> resolveTenantSlug(HttpServletRequest request) {
    String uri = request.getRequestURI();
    if (uri == null) return Optional.empty();
    Matcher m = MERCHANT_SLUG.matcher(uri);
    if (!m.matches()) return Optional.empty();
    String slug = m.group(2);
    if (slug == null || slug.isBlank()) return Optional.empty();
    return Optional.of(slug.trim());
  }
}

