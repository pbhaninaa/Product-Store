package com.productstore.platform.services;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminBootstrapService implements ApplicationRunner {
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final PasswordHasher passwordHasher;
  private final String adminEmail;
  private final String adminPassword;
  private final boolean syncPassword;

  public PlatformAdminBootstrapService(
      UserRepository users,
      MembershipRepository memberships,
      PasswordHasher passwordHasher,
      @Value("${app.bootstrap.platformAdmin.email:}") String adminEmail,
      @Value("${app.bootstrap.platformAdmin.password:}") String adminPassword,
      @Value("${app.bootstrap.platformAdmin.syncPassword:false}") boolean syncPassword) {
    this.users = users;
    this.memberships = memberships;
    this.passwordHasher = passwordHasher;
    this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase();
    this.adminPassword = adminPassword == null ? "" : adminPassword;
    this.syncPassword = syncPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (adminEmail.isBlank() || adminPassword.isBlank()) return;
    ensurePlatformAdmin();
  }

  @Transactional
  void ensurePlatformAdmin() {
    Optional<UserEntity> existing = users.findByEmailIgnoreCase(adminEmail);
    UserEntity u;
    if (existing.isEmpty()) {
      UserEntity nu = new UserEntity();
      nu.id = UUID.randomUUID();
      nu.email = adminEmail;
      nu.passwordHash = passwordHasher.hash(adminPassword);
      nu.createdAt = Instant.now();
      u = users.save(nu);
    } else {
      u = existing.get();
      // One-shot ops reset: set PLATFORM_ADMIN_SYNC_PASSWORD=true, redeploy, then turn it off.
      if (syncPassword && !passwordHasher.matches(adminPassword, u.passwordHash)) {
        u.passwordHash = passwordHasher.hash(adminPassword);
        users.save(u);
      }
    }

    boolean has =
        memberships.findAllByUserId(u.id).stream().anyMatch(m -> m.role == Role.PLATFORM_ADMIN);
    if (!has) {
      MembershipEntity m = new MembershipEntity();
      m.id = UUID.randomUUID();
      m.userId = u.id;
      m.tenantId = null;
      m.role = Role.PLATFORM_ADMIN;
      m.createdAt = Instant.now();
      memberships.save(m);
    }
  }
}
