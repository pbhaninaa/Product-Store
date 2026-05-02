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

  public PlatformAdminBootstrapService(
      UserRepository users,
      MembershipRepository memberships,
      PasswordHasher passwordHasher,
      @Value("${app.bootstrap.platformAdmin.email:}") String adminEmail,
      @Value("${app.bootstrap.platformAdmin.password:}") String adminPassword) {
    this.users = users;
    this.memberships = memberships;
    this.passwordHasher = passwordHasher;
    this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase();
    this.adminPassword = adminPassword == null ? "" : adminPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (adminEmail.isBlank() || adminPassword.isBlank()) return;
    ensurePlatformAdmin();
  }

  @Transactional
  void ensurePlatformAdmin() {
    Optional<UserEntity> existing = users.findByEmailIgnoreCase(adminEmail);
    UserEntity u =
        existing.orElseGet(
            () -> {
              UserEntity nu = new UserEntity();
              nu.id = UUID.randomUUID();
              nu.email = adminEmail;
              nu.passwordHash = passwordHasher.hash(adminPassword);
              nu.createdAt = Instant.now();
              return users.save(nu);
            });

    // Ensure membership exists.
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

