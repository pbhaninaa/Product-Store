package com.productstore.platform.services;

import java.util.UUID;

import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.TenantRepository;

import org.springframework.stereotype.Service;

@Service
public class TenantAccessService {
  private final TenantRepository tenants;

  public TenantAccessService(TenantRepository tenants) {
    this.tenants = tenants;
  }

  public record TenantRef(UUID id, String slug) {}

  public TenantRef requireTenantBySlug(String slug) {
    TenantEntity t =
        tenants.findBySlug(slug).orElseThrow(() -> new IllegalArgumentException("tenant_not_found"));
    return new TenantRef(t.id, t.slug);
  }
}

