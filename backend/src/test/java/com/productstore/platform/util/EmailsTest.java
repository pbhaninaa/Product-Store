package com.productstore.platform.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailsTest {

  @Test
  void acceptsNormalAndLocalhostSeedEmails() {
    assertTrue(Emails.isValid("client@localhost"));
    assertTrue(Emails.isValid("pat@client.test"));
    assertTrue(Emails.isValid("  User@Example.COM  "));
    assertFalse(Emails.isValid("not-an-email"));
    assertFalse(Emails.isValid("a@b"));
    assertFalse(Emails.isValid(""));
  }
}
