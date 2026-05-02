package com.productstore.platform.services.multitenancy;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

public interface TenantResolver {
  Optional<String> resolveTenantSlug(HttpServletRequest request);
}

