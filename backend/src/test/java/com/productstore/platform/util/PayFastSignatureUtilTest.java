package com.productstore.platform.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PayFastSignatureUtilTest {

  @Test
  void buildSignature_md5OfConcatenatedFieldsWithoutPassphrase() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("amount", "100.00");
    fields.put("item_name", "Test");

    assertEquals("fab5e8de276145c93cea3a5ec30efce9", PayFastSignatureUtil.buildSignature(fields, null));
  }

  @Test
  void buildSignature_appendsUrlEncodedPassphrase() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("merchant_id", "10000100");
    fields.put("amount", "10.00");

    assertEquals("a927df6a5673cd518bab40d341e54301", PayFastSignatureUtil.buildSignature(fields, "pass phrase"));
  }

  @Test
  void signaturesMatch_isCaseInsensitiveAndRejectsMismatch() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("amount", "100.00");
    fields.put("item_name", "Test");
    String sig = PayFastSignatureUtil.buildSignature(fields, null);

    assertTrue(PayFastSignatureUtil.signaturesMatch(sig, sig.toUpperCase()));
    assertFalse(PayFastSignatureUtil.signaturesMatch(sig, "deadbeef"));
  }
}
