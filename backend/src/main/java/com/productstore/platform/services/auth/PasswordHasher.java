package com.productstore.platform.services.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {
  private final PasswordEncoder encoder = new BCryptPasswordEncoder();

  public String hash(String raw) {
    return encoder.encode(raw);
  }

  /** A real hash to check against when the account doesn't exist, so the reply takes just as long. */
  private final String dummyHash = encoder.encode("timing-equaliser-not-a-password");

  public boolean matches(String raw, String hash) {
    return encoder.matches(raw, hash);
  }

  /**
   * Checks a password for a user who may not exist. Unknown accounts still pay for a bcrypt check, so response
   * time doesn't reveal which emails are registered.
   */
  public boolean matchesOrDummy(String raw, String hashOrNull) {
    if (hashOrNull == null || hashOrNull.isBlank()) {
      encoder.matches(raw == null ? "" : raw, dummyHash);
      return false;
    }
    return encoder.matches(raw, hashOrNull);
  }
}

