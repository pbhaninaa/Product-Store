package com.productstore.platform.services;

import java.time.Instant;
import java.util.UUID;

import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantProvisioningService {
  private final UserRepository users;
  private final TenantRepository tenants;
  private final MembershipRepository memberships;
  private final PasswordHasher passwordHasher;

  public MerchantProvisioningService(
      UserRepository users,
      TenantRepository tenants,
      MembershipRepository memberships,
      PasswordHasher passwordHasher) {
    this.users = users;
    this.tenants = tenants;
    this.memberships = memberships;
    this.passwordHasher = passwordHasher;
  }

  public record RegisteredMerchant(TenantEntity tenant, UserEntity owner) {}

  /**
   * Creates tenant + owner user + MERCHANT_OWNER membership. Same rules as public signup.
   */
  @Transactional
  public RegisteredMerchant registerMerchant(
      String merchantName, String merchantSlugRaw, String ownerEmailRaw, String ownerPassword) {
    String slug = TenantSlugUtil.normalize(merchantSlugRaw);
    if (tenants.findBySlug(slug).isPresent()) {
      throw new IllegalArgumentException("merchant_slug_taken");
    }
    String ownerEmail = ownerEmailRaw == null ? "" : ownerEmailRaw.trim().toLowerCase();
    if (ownerEmail.isEmpty()) throw new IllegalArgumentException("invalid_email");
    if (users.findByEmailIgnoreCase(ownerEmail).isPresent()) {
      throw new IllegalArgumentException("email_taken");
    }
    if (ownerPassword == null || ownerPassword.isBlank()) {
      throw new IllegalArgumentException("invalid_password");
    }

    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = slug;
    t.name = merchantName == null ? "" : merchantName.trim();
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenants.save(t);

    UserEntity u = new UserEntity();
    u.id = UUID.randomUUID();
    u.email = ownerEmail;
    u.passwordHash = passwordHasher.hash(ownerPassword);
    u.createdAt = Instant.now();
    users.save(u);

    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = u.id;
    m.tenantId = t.id;
    m.role = Role.MERCHANT_OWNER;
    m.createdAt = Instant.now();
    memberships.save(m);

    return new RegisteredMerchant(t, u);
  }
}
