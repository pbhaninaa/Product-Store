package com.productstore.platform.services.multitenancy;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

public final class TenantContextFilter extends OncePerRequestFilter {
  private final TenantResolver tenantResolver;

  public TenantContextFilter(TenantResolver tenantResolver) {
    this.tenantResolver = tenantResolver;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      tenantResolver.resolveTenantSlug(request).ifPresent(TenantContext::setTenantSlug);
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }
}

