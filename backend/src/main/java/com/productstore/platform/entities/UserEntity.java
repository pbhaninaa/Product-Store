package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {
  @Id
  public UUID id;

  @Column(nullable = false, unique = true)
  public String email;

  @Column(name = "password_hash", nullable = false)
  public String passwordHash;

  @Column(name = "display_name", length = 200)
  public String displayName;

  @Column(name = "referral_code", length = 32, unique = true)
  public String referralCode;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public boolean suspended = false;

  /** Login tokens issued before this instant are rejected (password change, suspension, removal). */
  @Column(name = "tokens_valid_after")
  public Instant tokensValidAfter;

  /** Signs the user out everywhere: every token issued before now stops working. */
  public void revokeTokens() {
    tokensValidAfter = Instant.now();
  }

  /** JWT {@code iat} has second precision, so compare whole seconds. */
  public boolean acceptsTokenIssuedAt(Instant issuedAt) {
    if (tokensValidAfter == null) return true;
    if (issuedAt == null) return false;
    return issuedAt.getEpochSecond() >= tokensValidAfter.getEpochSecond();
  }
}

