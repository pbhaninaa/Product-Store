package com.productstore.platform.services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.productstore.platform.entities.PasswordResetTokenEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.PasswordResetTokenRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.PasswordHasher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;
  private static final int MIN_PASSWORD_LENGTH = 8;

  private final UserRepository users;
  private final PasswordResetTokenRepository tokens;
  private final PasswordHasher passwordHasher;
  private final MerchantNotificationService notifications;
  private final Environment environment;
  private final String frontendBaseUrl;
  private final int tokenTtlHours;

  public PasswordResetService(
      UserRepository users,
      PasswordResetTokenRepository tokens,
      PasswordHasher passwordHasher,
      MerchantNotificationService notifications,
      Environment environment,
      @Value("${app.frontend-base-url:http://localhost:8085}") String frontendBaseUrl,
      @Value("${app.password-reset.token-ttl-hours:24}") int tokenTtlHours) {
    this.users = users;
    this.tokens = tokens;
    this.passwordHasher = passwordHasher;
    this.notifications = notifications;
    this.environment = environment;
    this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
    this.tokenTtlHours = Math.max(1, tokenTtlHours);
  }

  /** Always succeeds from the caller perspective (no email enumeration). */
  @Transactional
  public void requestReset(String email) {
    String trimmed = email == null ? "" : email.trim();
    if (trimmed.isBlank()) {
      return;
    }
    UserEntity user = users.findByEmailIgnoreCase(trimmed).orElse(null);
    if (user == null || user.suspended) {
      return;
    }

    tokens.deleteUnusedByUserId(user.id);

    String rawToken = generateRawToken();
    PasswordResetTokenEntity row = new PasswordResetTokenEntity();
    row.id = UUID.randomUUID();
    row.userId = user.id;
    row.tokenHash = hashToken(rawToken);
    row.expiresAt = Instant.now().plus(tokenTtlHours, ChronoUnit.HOURS);
    row.createdAt = Instant.now();
    tokens.save(row);

    String link = buildResetLink(rawToken);
    if (isLocalOrTestProfile()) {
      log.info("Password reset link for {}: {}", user.email, link);
    }

    String subject = "Reset your password";
    String message =
        "Reset your password\n\n"
            + "We received a request to reset your Product Store password.\n"
            + "Use this link to set a new password (expires in "
            + tokenTtlHours
            + " hours):\n\n"
            + link
            + "\n\nIf you did not request this, you can ignore this email.";
    notifications.sendTransactionalEmail(user.email, subject, message, EmailPurpose.SECURITY);
  }

  @Transactional
  public void resetPassword(String token, String newPassword) {
    String raw = token == null ? "" : token.trim();
    String next = newPassword == null ? "" : newPassword;
    if (raw.isBlank()) {
      throw new IllegalArgumentException("reset_invalid");
    }
    if (next.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException("password_too_short");
    }

    PasswordResetTokenEntity row =
        tokens.findByTokenHash(hashToken(raw)).orElseThrow(() -> new IllegalArgumentException("reset_invalid"));
    if (row.usedAt != null || row.expiresAt == null || row.expiresAt.isBefore(Instant.now())) {
      throw new IllegalArgumentException("reset_invalid");
    }

    UserEntity user =
        users.findById(row.userId).orElseThrow(() -> new IllegalArgumentException("reset_invalid"));
    if (user.suspended) {
      throw new IllegalArgumentException("account_suspended");
    }

    user.passwordHash = passwordHasher.hash(next);
    users.save(user);
    row.usedAt = Instant.now();
    tokens.save(row);
    tokens.deleteUnusedByUserId(user.id);
  }

  private String buildResetLink(String rawToken) {
    String base = sanitizeFrontendBaseUrl(frontendBaseUrl);
    if (base.isBlank()) {
      base = "http://localhost:8085";
    }
    String encoded = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    return base + "/reset-password?token=" + encoded;
  }

  static String sanitizeFrontendBaseUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String u = raw.trim();
    while (true) {
      String lower = u.toLowerCase();
      if (lower.startsWith("https://https://")
          || lower.startsWith("http://https://")
          || lower.startsWith("https://http://")
          || lower.startsWith("http://http://")) {
        u = u.substring(u.indexOf("://") + 3);
        continue;
      }
      break;
    }
    String lower = u.toLowerCase();
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
      u = "https://" + u;
    }
    while (u.endsWith("/")) {
      u = u.substring(0, u.length() - 1);
    }
    return u;
  }

  private static String generateRawToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to hash reset token", e);
    }
  }

  private boolean isLocalOrTestProfile() {
    for (String p : environment.getActiveProfiles()) {
      if ("local".equalsIgnoreCase(p) || "sit".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p)) {
        return true;
      }
    }
    return false;
  }
}
