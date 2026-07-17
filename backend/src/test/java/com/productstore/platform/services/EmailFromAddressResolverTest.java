package com.productstore.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmailFromAddressResolverTest {

  @Test
  void derivesEveryPurposeAddressFromConfiguredDomain() {
    EmailFromAddressResolver resolver =
        new EmailFromAddressResolver(" @Example.COM ", "legacy@example.net");

    assertEquals("info@example.com", resolver.resolve(EmailPurpose.INFO));
    assertEquals("support@example.com", resolver.resolve(EmailPurpose.SUPPORT));
    assertEquals("security@example.com", resolver.resolve(EmailPurpose.SECURITY));
    assertEquals("billing@example.com", resolver.resolve(EmailPurpose.BILLING));
    assertEquals("no-reply@example.com", resolver.resolve(EmailPurpose.NO_REPLY));
  }

  @Test
  void usesLegacyFromAddressWhenDomainIsBlank() {
    EmailFromAddressResolver resolver =
        new EmailFromAddressResolver("  ", " legacy@example.net ");

    assertEquals("legacy@example.net", resolver.resolve(EmailPurpose.INFO));
    assertEquals("legacy@example.net", resolver.resolve(EmailPurpose.NO_REPLY));
  }

  @Test
  void rejectsMissingPurpose() {
    EmailFromAddressResolver resolver =
        new EmailFromAddressResolver("example.com", "legacy@example.net");

    assertThrows(NullPointerException.class, () -> resolver.resolve(null));
  }
}
