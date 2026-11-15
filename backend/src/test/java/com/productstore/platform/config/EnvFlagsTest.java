package com.productstore.platform.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EnvFlagsTest {
  @Test
  void blankUsesDefault() {
    assertFalse(EnvFlags.parse("", false));
    assertTrue(EnvFlags.parse("  ", true));
    assertFalse(EnvFlags.parse(null, false));
  }

  @Test
  void parsesCommonTruthyFalseyValues() {
    assertTrue(EnvFlags.parse("true", false));
    assertTrue(EnvFlags.parse("YES", false));
    assertFalse(EnvFlags.parse("false", true));
    assertFalse(EnvFlags.parse("0", true));
  }
}
