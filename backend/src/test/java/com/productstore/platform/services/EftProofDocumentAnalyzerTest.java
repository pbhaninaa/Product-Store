package com.productstore.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class EftProofDocumentAnalyzerTest {

  private final EftProofDocumentAnalyzer analyzer = new EftProofDocumentAnalyzer();

  @Test
  void isPdfMagic_detectsPdfHeader() {
    assertFalse(analyzer.isPdfMagic(new byte[] {0x00}));
    assertTrue(analyzer.isPdfMagic(new byte[] {'%', 'P', 'D', 'F', '-'}));
  }

  @Test
  void resolveProofUploadExtension_acceptsPdfMagic() {
    byte[] pdf = new byte[] {'%', 'P', 'D', 'F', '-', 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    assertTrue("pdf".equals(analyzer.resolveProofUploadExtension("wrong.bin", pdf)));
  }

  @Test
  void imageProofsAreJudgedByTheirBytesNotTheirName() {
    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 1, 2};
    assertEquals("png", analyzer.resolveProofUploadExtension("slip.jpg", png));
    byte[] html = "<html><script>alert(1)</script></html>".getBytes();
    assertThrows(IllegalArgumentException.class, () -> analyzer.resolveProofUploadExtension("slip.png", html));
    assertThrows(IllegalArgumentException.class, () -> analyzer.resolveProofUploadExtension("slip.svg", html));
  }

  @Test
  void cashCodeGuessesAreCappedPerOrder() {
    String key = "order:" + java.util.UUID.randomUUID();
    for (int i = 0; i < CashCodeGuard.MAX_FAILURES; i++) {
      assertFalse(CashCodeGuard.check(key, "123456", "000000"));
    }
    // Even the right code is refused once the guesses are used up.
    assertThrows(RateLimiter.RateLimitedException.class, () -> CashCodeGuard.check(key, "123456", "123456"));
    assertTrue(CashCodeGuard.check("order:" + java.util.UUID.randomUUID(), "123 456", "123456"));
  }

  @Test
  void resolveProofUploadExtension_rejectsEmptyPayload() {
    assertThrows(IllegalArgumentException.class, () -> analyzer.resolveProofUploadExtension("x.pdf", new byte[0]));
  }

  @Test
  void parseMoneyToken_parsesSouthAfricanStyle() {
    Optional<BigDecimal> a = EftProofDocumentAnalyzer.parseMoneyToken("1 234,56");
    assertTrue(a.isPresent());
    assertTrue(a.get().compareTo(new BigDecimal("1234.56")) == 0);
  }
}
