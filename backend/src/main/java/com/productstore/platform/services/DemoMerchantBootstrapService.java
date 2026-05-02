package com.productstore.platform.services;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a demo merchant (default slug {@code demo}) when local credentials are configured. Safe to run
 * repeatedly: only inserts missing tenant, user, membership, or shop-settings row.
 */
@Service
@Order(10)
public class DemoMerchantBootstrapService implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DemoMerchantBootstrapService.class);

  private final TenantRepository tenants;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final ShopSettingsRepository shopSettings;
  private final PasswordHasher passwordHasher;

  private final boolean enabled;
  private final String slug;
  private final String displayName;
  private final String ownerEmail;
  private final String ownerPassword;

  public DemoMerchantBootstrapService(
      TenantRepository tenants,
      UserRepository users,
      MembershipRepository memberships,
      ShopSettingsRepository shopSettings,
      PasswordHasher passwordHasher,
      @Value("${app.bootstrap.demoMerchant.enabled:false}") boolean enabled,
      @Value("${app.bootstrap.demoMerchant.slug:demo}") String slug,
      @Value("${app.bootstrap.demoMerchant.displayName:Demo Store}") String displayName,
      @Value("${app.bootstrap.demoMerchant.ownerEmail:}") String ownerEmail,
      @Value("${app.bootstrap.demoMerchant.ownerPassword:}") String ownerPassword) {
    this.tenants = tenants;
    this.users = users;
    this.memberships = memberships;
    this.shopSettings = shopSettings;
    this.passwordHasher = passwordHasher;
    this.enabled = enabled;
    this.slug = slug == null ? "" : slug.trim().toLowerCase();
    this.displayName = displayName == null ? "Demo Store" : displayName.trim();
    this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim().toLowerCase();
    this.ownerPassword = ownerPassword == null ? "" : ownerPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled || slug.isBlank() || ownerEmail.isBlank() || ownerPassword.isBlank()) {
      return;
    }
    ensureDemoMerchant();
  }

  @Transactional
  protected void ensureDemoMerchant() {
    boolean createdTenant = false;
    TenantEntity tenant =
        tenants
            .findBySlug(slug)
            .orElse(null);
    if (tenant == null) {
      TenantEntity t = new TenantEntity();
      t.id = UUID.randomUUID();
      t.slug = slug;
      t.name = displayName.isBlank() ? "Demo Store" : displayName;
      t.modulesJson = "{}";
      t.createdAt = Instant.now();
      tenant = tenants.save(t);
      createdTenant = true;
    }

    boolean createdUser = false;
    UserEntity user = users.findByEmailIgnoreCase(ownerEmail).orElse(null);
    if (user == null) {
      UserEntity u = new UserEntity();
      u.id = UUID.randomUUID();
      u.email = ownerEmail;
      u.passwordHash = passwordHasher.hash(ownerPassword);
      u.createdAt = Instant.now();
      user = users.save(u);
      createdUser = true;
    }

    UUID tenantId = tenant.id;
    boolean hasMerchantMembership =
        memberships.findAllByUserId(user.id).stream()
            .anyMatch(
                m ->
                    tenantId.equals(m.tenantId)
                        && (m.role == Role.MERCHANT_OWNER || m.role == Role.MERCHANT_STAFF));
    boolean createdMembership = false;
    if (!hasMerchantMembership) {
      MembershipEntity m = new MembershipEntity();
      m.id = UUID.randomUUID();
      m.userId = user.id;
      m.tenantId = tenant.id;
      m.role = Role.MERCHANT_OWNER;
      m.createdAt = Instant.now();
      memberships.save(m);
      createdMembership = true;
    }

    boolean createdShopSettings = shopSettings.findByTenantId(tenant.id).isEmpty();
    if (createdShopSettings) {
      shopSettings.save(ShopSettingsDefaults.newRowForTenant(tenant.id));
    }

    if (createdTenant || createdUser || createdMembership || createdShopSettings) {
      log.info(
          "Demo merchant ready (bootstrap): storefront=/m/{} admin=/m/{}/admin ownerEmail={}",
          slug,
          slug,
          ownerEmail);
    }
  }
}
