package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.SubscriptionPlanPricingEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.PlatformBankingRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.SubscriptionPlanPricingRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * CTO rule: the system never shows a dash to a person. Every notification title/body and email
 * subject/body these services build must be free of hyphen, figure, en, em dashes and minus signs.
 */
class UserFacingTextHasNoDashesTest {
  private static final Pattern DASH = Pattern.compile("[-‒–—−]");

  private final List<String> emailTexts = Collections.synchronizedList(new ArrayList<>());
  private HttpServer sendgrid;

  private final UUID tenantId = UUID.randomUUID();
  private final TenantRepository tenants = mock(TenantRepository.class);
  private final ShopSettingsRepository shopSettings = mock(ShopSettingsRepository.class);
  private final InAppNotificationService inApp = mock(InAppNotificationService.class);
  private final MerchantSubscriptionService subscriptions = mock(MerchantSubscriptionService.class);

  @BeforeEach
  void startFakeSendgrid() throws Exception {
    sendgrid = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    sendgrid.createContext(
        "/",
        exchange -> {
          JsonNode n =
              new ObjectMapper()
                  .readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          emailTexts.add(n.at("/subject").asText());
          emailTexts.add(n.at("/content/0/value").asText());
          exchange.sendResponseHeaders(202, -1);
          exchange.close();
        });
    sendgrid.start();

    TenantEntity tenant = new TenantEntity();
    tenant.id = tenantId;
    tenant.name = "Sunny Shop";
    tenant.slug = "sunnyshop";
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.tenantId = tenantId;
    s.contactEmail = "owner@shop.test";
    when(shopSettings.findByTenantId(tenantId)).thenReturn(Optional.of(s));
    when(subscriptions.grantsFeature(any(), eq("emailAlerts"))).thenReturn(true);
  }

  @AfterEach
  void stopFakeSendgrid() {
    sendgrid.stop(0);
  }

  private MerchantNotificationService notificationService() {
    return new MerchantNotificationService(
        tenants,
        shopSettings,
        inApp,
        subscriptions,
        new EmailFromAddressResolver("shop.test", "noreply@shop.test"),
        "test-key",
        "http://127.0.0.1:" + sendgrid.getAddress().getPort() + "/v3/mail/send",
        "false",
        "",
        "",
        "");
  }

  @Test
  void merchantNotificationTitlesBodiesAndEmailsHaveNoDashes() {
    MerchantNotificationService svc = notificationService();

    OrderEntity order = new OrderEntity();
    order.id = UUID.randomUUID();
    order.customerName = "Thandi Mokoena";
    order.customerEmail = "thandi@client.test";
    order.totalZar = new BigDecimal("150.00");
    order.paymentReferenceDeclared = "ORDER123";
    order.deliveryType = OrderEntity.DeliveryType.delivery;

    SalonBookingEntity booking = new SalonBookingEntity();
    booking.id = UUID.randomUUID();
    booking.customerName = "Thandi Mokoena";
    booking.customerEmail = "thandi@client.test";
    booking.startAt = Instant.parse("2026-11-22T08:30:00Z");
    booking.paymentReferenceDeclared = "BOOK123";

    svc.notifyOrderPlaced(tenantId, order);
    svc.notifyOrderNeedsManualEftReview(tenantId, order);
    svc.notifyOrderPaid(tenantId, order);
    for (OrderEntity.FulfillmentStatus f : OrderEntity.FulfillmentStatus.values()) {
      order.fulfillmentStatus = f;
      svc.notifyOrderFulfillmentChanged(tenantId, order);
    }
    svc.notifyBookingPlaced(tenantId, booking);
    svc.notifyBookingNeedsManualEftReview(tenantId, booking);
    for (SalonBookingEntity.Status st : SalonBookingEntity.Status.values()) {
      booking.status = st;
      svc.notifyBookingStatusChanged(tenantId, booking);
    }

    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(inApp, atLeastOnce())
        .notifyTenantStaff(eq(tenantId), title.capture(), body.capture(), anyString(), anyString(), anyString());

    assertThat(title.getAllValues()).isNotEmpty().allSatisfy(UserFacingTextHasNoDashesTest::assertNoDash);
    assertThat(body.getAllValues()).isNotEmpty().allSatisfy(UserFacingTextHasNoDashesTest::assertNoDash);
    assertThat(title.getAllValues()).contains("New booking placed: " + com.productstore.platform.util.RefCodes.shortRef(booking.id));
    // Email delivery is synchronous, so every subject/body has been captured by now.
    assertThat(emailTexts).isNotEmpty().allSatisfy(UserFacingTextHasNoDashesTest::assertNoDash);
  }

  @Test
  void subscriptionNotificationsAndPaymentReferenceHaveNoDashes() {
    MerchantSubscriptionRepository subRepo = mock(MerchantSubscriptionRepository.class);
    SubscriptionPlanPricingRepository plans = mock(SubscriptionPlanPricingRepository.class);
    MerchantSubscriptionEntity sub = new MerchantSubscriptionEntity();
    sub.tenantId = tenantId;
    when(subRepo.findByTenantId(tenantId)).thenReturn(Optional.of(sub));
    when(subRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    SubscriptionPlanPricingEntity pricing = new SubscriptionPlanPricingEntity();
    pricing.tier = TenantEntity.SubscriptionPlan.STANDARD;
    when(plans.findByTier(any())).thenReturn(Optional.of(pricing));
    tenants.findById(tenantId).get().createdAt = Instant.parse("2026-11-22T08:30:00Z");

    @SuppressWarnings("unchecked")
    ObjectProvider<Clock> clock = mock(ObjectProvider.class);
    when(clock.getIfAvailable(any())).thenReturn(Clock.systemUTC());
    MerchantSubscriptionService svc =
        new MerchantSubscriptionService(
            subRepo,
            plans,
            mock(PlatformBankingRepository.class),
            tenants,
            mock(EmployeeRepository.class),
            mock(ProductRepository.class),
            mock(EftProofDocumentAnalyzer.class),
            inApp,
            mock(OnlinePaymentAvailability.class),
            mock(ReferralService.class),
            "./target/test-uploads",
            clock);

    svc.provisionNewMerchantSubscription(tenantId);
    assertNoDash(sub.paymentProofAutoSummary);
    assertThat(sub.mandatoryPaymentReference).startsWith("PSSUNNYSH");
    assertNoDash(sub.mandatoryPaymentReference);

    sub.planTier = TenantEntity.SubscriptionPlan.STANDARD;
    svc.activatePeriod(tenantId);

    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(inApp, atLeastOnce())
        .notifyTenantStaff(eq(tenantId), title.capture(), body.capture(), anyString(), anyString(), anyString());
    assertThat(title.getAllValues()).hasSizeGreaterThanOrEqualTo(2).allSatisfy(UserFacingTextHasNoDashesTest::assertNoDash);
    assertThat(body.getAllValues()).hasSizeGreaterThanOrEqualTo(2).allSatisfy(UserFacingTextHasNoDashesTest::assertNoDash);
  }

  private static void assertNoDash(String text) {
    assertThat(text).as("user facing text").isNotNull();
    assertThat(DASH.matcher(text).find()).as("dash in user facing text: \"%s\"", text).isFalse();
  }
}
