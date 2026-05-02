package com.productstore.platform.services;

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
