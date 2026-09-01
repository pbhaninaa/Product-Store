package com.productstore.platform.services;

import java.time.Instant;
import java.util.UUID;

import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Seeds one CLIENT account on the {@code sit} profile only. Safe to run repeatedly. */
@Service
@Order(30)
public class DemoClientBootstrapService implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DemoClientBootstrapService.class);

  private final UserRepository users;
  private final MembershipRepository memberships;
  private final PasswordHasher passwordHasher;
  private final MerchantProvisioningService merchantProvisioning;
  private final Environment environment;
  private final boolean enabled;
  private final String email;
  private final String password;
  private final String displayName;

  public DemoClientBootstrapService(
      UserRepository users,
      MembershipRepository memberships,
      PasswordHasher passwordHasher,
      MerchantProvisioningService merchantProvisioning,
      Environment environment,
      @Value("${app.bootstrap.demoClient.enabled:false}") boolean enabled,
      @Value("${app.bootstrap.demoClient.email:}") String email,
      @Value("${app.bootstrap.demoClient.password:}") String password,
      @Value("${app.bootstrap.demoClient.displayName:Demo Client}") String displayName) {
    this.users = users;
    this.memberships = memberships;
    this.passwordHasher = passwordHasher;
    this.merchantProvisioning = merchantProvisioning;
    this.environment = environment;
    this.enabled = enabled;
    this.email = email == null ? "" : email.trim().toLowerCase();
    this.password = password == null ? "" : password;
    this.displayName = displayName == null || displayName.isBlank() ? "Demo Client" : displayName.trim();
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled || email.isBlank() || password.isBlank()) return;
    if (!isSitProfile()) {
      log.warn("Skipping demo client bootstrap; sit profile is not active");
      return;
    }
    ensureDemoClient();
  }

  private boolean isSitProfile() {
    for (String p : environment.getActiveProfiles()) {
      if ("sit".equalsIgnoreCase(p)) return true;
    }
    return false;
  }

  @Transactional
  protected void ensureDemoClient() {
    UserEntity user = users.findByEmailIgnoreCase(email).orElse(null);
    if (user == null) {
      UserEntity u = new UserEntity();
      u.id = UUID.randomUUID();
      u.email = email;
      u.passwordHash = passwordHasher.hash(password);
      u.displayName = displayName;
      u.createdAt = Instant.now();
      user = users.save(u);
    }

    boolean hasClient =
        memberships.findAllByUserId(user.id).stream().anyMatch(m -> m.role == Role.CLIENT);
    if (!hasClient) {
      MembershipEntity m = new MembershipEntity();
      m.id = UUID.randomUUID();
      m.userId = user.id;
      m.tenantId = null;
      m.role = Role.CLIENT;
      m.createdAt = Instant.now();
      memberships.save(m);
    }

    merchantProvisioning.finishClientSignup(user, null);
    log.info("Demo client ready: email={}", email);
  }
}
