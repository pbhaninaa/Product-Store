package com.productstore.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.productstore.platform.entities.PeachPaymentMethod;

import org.junit.jupiter.api.Test;

class PeachPaymentMethodTest {
  @Test
  void parsesOnlyCardAndEft() {
    assertEquals(PeachPaymentMethod.CARD, PeachPaymentMethod.fromRequest("card"));
    assertEquals(PeachPaymentMethod.EFT, PeachPaymentMethod.fromRequest(" EFT "));
    assertEquals(
        "peach_payment_method_required",
        assertThrows(IllegalArgumentException.class, () -> PeachPaymentMethod.fromRequest(null))
            .getMessage());
    assertEquals(
        "invalid_peach_payment_method",
        assertThrows(IllegalArgumentException.class, () -> PeachPaymentMethod.fromRequest("PAYBYBANK"))
            .getMessage());
  }

  @Test
  void mapsCardAndEftToForcedHostedCheckoutMethods() {
    Map<String, Object> card = new LinkedHashMap<>();
    PeachPaymentService.addPaymentMethodFields(card, PeachPaymentMethod.CARD);
    assertEquals("CARD", card.get("defaultPaymentMethod"));
    assertEquals(true, card.get("forceDefaultMethod"));

    Map<String, Object> eft = new LinkedHashMap<>();
    PeachPaymentService.addPaymentMethodFields(eft, PeachPaymentMethod.EFT);
    assertEquals("PAYBYBANK", eft.get("defaultPaymentMethod"));
    assertEquals(true, eft.get("forceDefaultMethod"));

    Map<String, Object> legacyNull = new LinkedHashMap<>();
    PeachPaymentService.addPaymentMethodFields(legacyNull, null);
    assertEquals("CARD", legacyNull.get("defaultPaymentMethod"));
    assertEquals(true, legacyNull.get("forceDefaultMethod"));
  }

  @Test
  void hostedCheckoutMethodCodes() {
    assertEquals("CARD", PeachPaymentMethod.CARD.hostedCheckoutMethod());
    assertEquals("PAYBYBANK", PeachPaymentMethod.EFT.hostedCheckoutMethod());
  }

  @Test
  void notificationPayloadRequiresZarAmountAndMatchingReference() {
    Map<String, String> ok = new LinkedHashMap<>();
    ok.put("currency", "ZAR");
    ok.put("amount", "100.00");
    ok.put("merchantTransactionId", "abc123def456");
    PeachPaymentService.assertNotificationPayload(ok, new BigDecimal("100.00"), "abc123def456");

    Map<String, String> badCurrency = new LinkedHashMap<>(ok);
    badCurrency.put("currency", "USD");
    assertEquals(
        "peach_currency_mismatch",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    PeachPaymentService.assertNotificationPayload(
                        badCurrency, new BigDecimal("100.00"), "abc123def456"))
            .getMessage());

    Map<String, String> badAmount = new LinkedHashMap<>(ok);
    badAmount.put("amount", "50.00");
    assertEquals(
        "peach_amount_mismatch",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    PeachPaymentService.assertNotificationPayload(
                        badAmount, new BigDecimal("100.00"), "abc123def456"))
            .getMessage());

    Map<String, String> badRef = new LinkedHashMap<>(ok);
    badRef.put("merchantTransactionId", "other");
    assertEquals(
        "peach_reference_mismatch",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    PeachPaymentService.assertNotificationPayload(
                        badRef, new BigDecimal("100.00"), "abc123def456"))
            .getMessage());
  }
}
