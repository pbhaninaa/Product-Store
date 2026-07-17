package com.productstore.platform.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.config.PeachProperties;
import com.productstore.platform.entities.PeachPaymentMethod;
import com.productstore.platform.entities.SubscriptionPeachPaymentEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.SubscriptionPeachPaymentRepository;
import com.productstore.platform.repositories.SubscriptionPlanPricingRepository;
import com.productstore.platform.util.PeachSignatureUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SubscriptionPeachWebhookTest {
  private SubscriptionPeachPaymentRepository subscriptionPeachPayments;
  private MerchantSubscriptionRepository merchantSubscriptions;
  private MerchantSubscriptionService merchantSubscriptionService;
  private PeachPaymentService service;
  private SubscriptionPeachPaymentEntity payment;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    subscriptionPeachPayments = mock(SubscriptionPeachPaymentRepository.class);
    merchantSubscriptions = mock(MerchantSubscriptionRepository.class);
    merchantSubscriptionService = mock(MerchantSubscriptionService.class);
    PeachProperties properties = new PeachProperties();
    ReflectionTestUtils.setField(properties, "enabled", true);
    ReflectionTestUtils.setField(properties, "clientId", "client");
    ReflectionTestUtils.setField(properties, "clientSecret", "secret");
    ReflectionTestUtils.setField(properties, "merchantId", "merchant");
    ReflectionTestUtils.setField(properties, "entityId", "entity");
    ReflectionTestUtils.setField(properties, "secretToken", "webhook-secret");

    service =
        new PeachPaymentService(
            properties,
            mock(OrderRepository.class),
            mock(SalonBookingRepository.class),
            mock(SalonServiceRepository.class),
            mock(ShopSettingsRepository.class),
            merchantSubscriptions,
            mock(SubscriptionPlanPricingRepository.class),
            subscriptionPeachPayments,
            mock(CheckoutService.class),
            mock(SalonBookingService.class),
            merchantSubscriptionService,
            "http://localhost:8085",
            "http://localhost:8080");

    tenantId = UUID.randomUUID();
    payment = new SubscriptionPeachPaymentEntity();
    payment.id = UUID.randomUUID();
    payment.tenantId = tenantId;
    payment.planTier = TenantEntity.SubscriptionPlan.STANDARD;
    payment.amount = new BigDecimal("199.00");
    payment.currency = "ZAR";
    payment.status = SubscriptionPeachPaymentEntity.STATUS_PENDING;
    payment.peachPaymentMethod = PeachPaymentMethod.CARD;
    payment.peachMerchantTransactionId = "SUB1234567890123";
    payment.peachCheckoutId = "checkout-sub-1";

    when(subscriptionPeachPayments.findByPeachMerchantTransactionIdForUpdate("SUB1234567890123"))
        .thenReturn(Optional.of(payment));
    when(subscriptionPeachPayments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(merchantSubscriptions.findByTenantId(tenantId)).thenReturn(Optional.empty());
  }

  @Test
  void signedMatchingSuccessActivatesOnceAndIsIdempotent() {
    Map<String, String> params = signedParams("199.00");

    service.handleWebhook(params);
    service.handleWebhook(params);

    assertEquals(SubscriptionPeachPaymentEntity.STATUS_COMPLETED, payment.status);
    assertNotNull(payment.completedAt);
    verify(merchantSubscriptionService, times(1))
        .finalizePeachPaidSubscription(eq(tenantId), eq(TenantEntity.SubscriptionPlan.STANDARD));
  }

  @Test
  void amountMismatchNeverActivates() {
    Map<String, String> params = signedParams("198.00");

    assertThrows(IllegalArgumentException.class, () -> service.handleWebhook(params));

    assertEquals(SubscriptionPeachPaymentEntity.STATUS_PENDING, payment.status);
    verify(merchantSubscriptionService, never())
        .finalizePeachPaidSubscription(any(), any());
  }

  @Test
  void unsignedNotificationNeverActivates() {
    Map<String, String> params = signedParams("199.00");
    params.remove("signature");

    assertThrows(IllegalArgumentException.class, () -> service.handleWebhook(params));

    verify(subscriptionPeachPayments, never()).findByPeachMerchantTransactionIdForUpdate(any());
    verify(merchantSubscriptionService, never())
        .finalizePeachPaidSubscription(any(), any());
  }

  @Test
  void eftPayByBankRailStillSettlesViaLedger() {
    payment.peachPaymentMethod = PeachPaymentMethod.EFT;
    Map<String, String> params = signedParams("199.00");

    service.handleWebhook(params);

    assertEquals(SubscriptionPeachPaymentEntity.STATUS_COMPLETED, payment.status);
    verify(merchantSubscriptionService, times(1))
        .finalizePeachPaidSubscription(eq(tenantId), eq(TenantEntity.SubscriptionPlan.STANDARD));
  }

  private Map<String, String> signedParams(String amount) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("amount", amount);
    params.put("checkoutId", "checkout-sub-1");
    params.put("currency", "ZAR");
    params.put("merchantTransactionId", "SUB1234567890123");
    params.put("paymentType", "DB");
    params.put("status", "successful");
    params.put("signature", PeachSignatureUtil.buildSignature(params, "webhook-secret"));
    return params;
  }
}
