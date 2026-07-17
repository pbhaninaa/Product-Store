package com.productstore.platform.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PeachSignatureUtilTest {

  @Test
  void buildSignature_matchesDocumentedConcatenationAlgorithm() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("paymentType", "DB");
    fields.put("currency", "ZAR");
    fields.put("amount", "2");
    fields.put("authentication.entityId", "8ac7a4ca68c22c4d0168c2caab2e0025");
    fields.put("defaultPaymentMethod", "CARD");
    fields.put("merchantTransactionId", "Test1234");
    fields.put("nonce", "JHGJSGHDSKJHGJDHGJH");
    fields.put("shopperResultUrl", "https://example.com/example-webhook");

    String secret = "3fcd7cf22f55119eadbe02d14de18c0c";
    String expected = "fc1273384a7806c00a6e0512e902be4ed2181af8b72030653310dfc385d1eab4";

    String actual = PeachSignatureUtil.buildSignature(fields, secret);

    assertEquals(expected, actual);
  }

  @Test
  void signaturesMatch_isCaseInsensitiveAndRejectsMismatch() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("amount", "100.00");
    fields.put("currency", "ZAR");
    String sig = PeachSignatureUtil.buildSignature(fields, "secret");

    assertEquals(true, PeachSignatureUtil.signaturesMatch(sig, sig.toUpperCase()));
    assertFalse(PeachSignatureUtil.signaturesMatch(sig, "deadbeef"));
  }
}
