package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.config.PaystackProperties;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.OnlinePaymentChannel;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;
import com.productstore.platform.entities.SubscriptionPlanPricingEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.models.OnlineCheckoutSession;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.SubscriptionOnlinePaymentRepository;
import com.productstore.platform.repositories.SubscriptionPlanPricingRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.OnlinePaymentSettlementService.ConfirmedPayment;
import com.productstore.platform.services.OnlinePaymentSettlementService.Outcome;
import com.productstore.platform.services.PaystackPaymentService.VerifyResult;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

class PaystackPaymentServiceTest {
  private static final String SECRET = "sk_test_unit_secret";
  private static final String ORDER_REF = "ORDA1B2C3D4E5F6G7H8";
  private static final String OLDER_ORDER_REF = "ORDZZZZYYYYXXXXWWWW";
  private static final String BOOKING_REF = "BKGA1B2C3D4E5F6G7H8";
  private static final String SUB_REF = "SUBA1B2C3D4E5F6G7H8";

  private PaystackProperties properties;
  private PaystackClient client;
  private PlatformFeatureService features;
  private OnlinePaymentSettlementService settlement;
  private OnlinePaymentAttemptRepository attempts;
  private OrderRepository orders;
  private SalonBookingRepository bookings;
  private SalonServiceRepository salonServices;
  private ShopSettingsRepository shopSettings;
  private MerchantSubscriptionRepository merchantSubscriptions;
  private SubscriptionPlanPricingRepository plans;
  private SubscriptionOnlinePaymentRepository subscriptionPayments;
  private MembershipRepository memberships;
  private UserRepository users;
  private CheckoutService checkout;
  private SalonBookingService salon;
  private MerchantSubscriptionService subscriptionService;
  private PaystackPaymentService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    properties = new PaystackProperties();
    ReflectionTestUtils.setField(properties, "enabled", "true");
    ReflectionTestUtils.setField(properties, "secretKey", SECRET);
    ReflectionTestUtils.setField(properties, "baseUrl", "https://api.paystack.co");
    ReflectionTestUtils.setField(properties, "verifyCacheTtl", Duration.ZERO);
    ReflectionTestUtils.setField(properties, "environment", environment("uat"));
    client = mock(PaystackClient.class);
    features = mock(PlatformFeatureService.class);
    when(features.isEnabled(any())).thenReturn(true);
    settlement = mock(OnlinePaymentSettlementService.class);
    attempts = mock(OnlinePaymentAttemptRepository.class);
    when(attempts.findByKindAndRecordIdAndOutcome(any(), any(), any())).thenReturn(List.of());
    when(attempts.findByKindAndRecordIdAndReference(any(), any(), any())).thenReturn(Optional.empty());
    orders = mock(OrderRepository.class);
    bookings = mock(SalonBookingRepository.class);
    salonServices = mock(SalonServiceRepository.class);
    shopSettings = mock(ShopSettingsRepository.class);
    when(shopSettings.findByTenantId(any())).thenReturn(Optional.empty());
    merchantSubscriptions = mock(MerchantSubscriptionRepository.class);
    plans = mock(SubscriptionPlanPricingRepository.class);
    subscriptionPayments = mock(SubscriptionOnlinePaymentRepository.class);
    memberships = mock(MembershipRepository.class);
    users = mock(UserRepository.class);
    checkout = mock(CheckoutService.class);
    salon = mock(SalonBookingService.class);
    subscriptionService = mock(MerchantSubscriptionService.class);
    service =
        new PaystackPaymentService(
            properties,
            client,
            new PaystackVerifier(client, properties),
            new OnlinePaymentAvailability(properties, features),
            settlement,
            attempts,
            orders,
            bookings,
            salonServices,
            shopSettings,
            merchantSubscriptions,
            plans,
            subscriptionPayments,
            memberships,
            users,
            checkout,
            salon,
            subscriptionService,
            new ObjectMapper(),
            mock(PlatformTransactionManager.class),
            "https://shop.test/");
  }

  // ------------------------------------------------------------------ webhook

  @Test
  void signatureIsHexHmacSha512OfTheRawBody() throws Exception {
    byte[] body = "{\"event\":\"charge.success\"}".getBytes(StandardCharsets.UTF_8);
    String good = sign(body, SECRET);

    assertThat(PaystackPaymentService.signatureMatches(body, good, SECRET)).isTrue();
    assertThat(PaystackPaymentService.signatureMatches(body, good.toUpperCase(), SECRET)).isTrue();
    assertThat(PaystackPaymentService.signatureMatches(body, sign(body, "sk_test_other"), SECRET)).isFalse();
    byte[] altered = "{\"event\":\"charge.success\" }".getBytes(StandardCharsets.UTF_8);
    assertThat(PaystackPaymentService.signatureMatches(altered, good, SECRET)).isFalse();
    assertThat(PaystackPaymentService.signatureMatches(body, null, SECRET)).isFalse();
    assertThat(PaystackPaymentService.signatureMatches(body, good, "")).isFalse();
  }

  @Test
  void webhookWithInvalidSignatureSettlesNothing() {
    byte[] body = chargeSuccess(ORDER_REF, 15050, "{\"kind\":\"order\",\"id\":\"x\"}", "test");

    assertThatThrownBy(() -> service.handleWebhook(body, "0".repeat(128)))
        .isInstanceOf(PaystackPaymentService.InvalidWebhookSignatureException.class);
    assertThatThrownBy(() -> service.handleWebhook(body, null))
        .isInstanceOf(PaystackPaymentService.InvalidWebhookSignatureException.class);
    verify(settlement, never()).settle(any());
  }

  @Test
  void webhookWithoutConfiguredSecretIsRejected() throws Exception {
    ReflectionTestUtils.setField(properties, "secretKey", "");
    byte[] body = chargeSuccess(ORDER_REF, 100, "{}", "test");

    assertThatThrownBy(() -> service.handleWebhook(body, sign(body, "")))
        .isInstanceOf(PaystackPaymentService.InvalidWebhookSignatureException.class);
  }

  @Test
  void signedChargeSuccessIsSettledWithPaystackDetails() throws Exception {
    String orderId = UUID.randomUUID().toString();
    byte[] body = chargeSuccess(ORDER_REF, 15050, "{\"kind\":\"order\",\"id\":\"" + orderId + "\"}", "test", "eft");
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);

    assertThat(service.handleWebhook(body, sign(body, SECRET))).isEqualTo(Outcome.SETTLED);

    ArgumentCaptor<ConfirmedPayment> paid = ArgumentCaptor.forClass(ConfirmedPayment.class);
    verify(settlement).settle(paid.capture());
    assertThat(paid.getValue())
        .isEqualTo(new ConfirmedPayment(ORDER_REF, "302961", 15050, "ZAR", OnlinePaymentChannel.EFT, "order", orderId));
  }

  @Test
  void requestedAmountIsComparedWhenPaystackSendsIt() throws Exception {
    // A customer-borne fee raises "amount"; "requested_amount" is what the record asked for.
    String json =
        "{\"event\":\"charge.success\",\"data\":{\"id\":1,\"domain\":\"test\",\"status\":\"success\","
            + "\"reference\":\"" + ORDER_REF + "\",\"amount\":15500,\"requested_amount\":15050,"
            + "\"currency\":\"ZAR\",\"channel\":\"card\",\"metadata\":{}}}";
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);

    service.handleWebhook(body, sign(body, SECRET));

    ArgumentCaptor<ConfirmedPayment> paid = ArgumentCaptor.forClass(ConfirmedPayment.class);
    verify(settlement).settle(paid.capture());
    assertThat(paid.getValue().amountCents()).isEqualTo(15050);
  }

  /** AC-6: the webhook passes the Paystack fee the merchant bears; none sent means unknown (null), not 0. */
  @Test
  void webhookPassesThePaystackFeeTheMerchantBears() throws Exception {
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);
    ArgumentCaptor<ConfirmedPayment> paid = ArgumentCaptor.forClass(ConfirmedPayment.class);

    // Fee borne by the merchant (no requested_amount): all of it.
    byte[] plain = chargeSuccessJson(ORDER_REF, "\"amount\":100000,\"fees\":2500");
    service.handleWebhook(plain, sign(plain, SECRET));
    // Charges passed to the customer: the R4.50 surcharge is not the merchant's.
    byte[] passed = chargeSuccessJson(ORDER_REF, "\"amount\":15500,\"requested_amount\":15050,\"fees\":650");
    service.handleWebhook(passed, sign(passed, SECRET));
    // No fee in the payload.
    byte[] none = chargeSuccessJson(ORDER_REF, "\"amount\":15050,\"fees\":null");
    service.handleWebhook(none, sign(none, SECRET));

    verify(settlement, times(3)).settle(paid.capture());
    assertThat(paid.getAllValues()).extracting(ConfirmedPayment::feeCents).containsExactly(2500L, 200L, null);
  }

  @Test
  void verifyPassesThePaystackFee() {
    UUID id = UUID.randomUUID();
    PaystackClient.Transaction t = transaction(ORDER_REF, "success", 15050, "order", id);
    when(client.verify(ORDER_REF))
        .thenReturn(
            Optional.of(
                new PaystackClient.Transaction(
                    t.id(), t.status(), t.reference(), t.chargedCents(), null, t.currency(), t.channel(),
                    t.domain(), t.metadata(), 376L)));
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);

    assertThat(service.verifyAndSettle(ORDER_REF, "order", id)).isEqualTo(VerifyResult.SETTLED);

    ArgumentCaptor<ConfirmedPayment> paid = ArgumentCaptor.forClass(ConfirmedPayment.class);
    verify(settlement).settle(paid.capture());
    assertThat(paid.getValue().feeCents()).isEqualTo(376L);
  }

  private static byte[] chargeSuccessJson(String reference, String amountsJson) {
    String json =
        "{\"event\":\"charge.success\",\"data\":{\"id\":302961,\"domain\":\"test\",\"status\":\"success\","
            + "\"reference\":\"" + reference + "\"," + amountsJson + ",\"currency\":\"ZAR\","
            + "\"channel\":\"card\",\"metadata\":{}}}";
    return json.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void anotherAppsTransactionOnTheSharedAccountIsIgnored() throws Exception {
    byte[] body = chargeSuccess("T685312322670591", 5000, "{\"kind\":\"order\",\"id\":\"x\"}", "test");

    assertThat(service.handleWebhook(body, sign(body, SECRET))).isEqualTo(Outcome.NOT_FOUND);
    verify(settlement, never()).settle(any());
  }

  @Test
  void prodRefusesTestTransactions() throws Exception {
    ReflectionTestUtils.setField(properties, "environment", environment("prod"));
    byte[] test = chargeSuccess(ORDER_REF, 100, "{}", "test");
    assertThatThrownBy(() -> service.handleWebhook(test, sign(test, SECRET))).hasMessage("online_payment_not_live");
    verify(settlement, never()).settle(any());

    byte[] live = chargeSuccess(ORDER_REF, 100, "{}", "live");
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);
    assertThat(service.handleWebhook(live, sign(live, SECRET))).isEqualTo(Outcome.SETTLED);
  }

  @Test
  void metadataSentAsJsonStringIsRead() throws Exception {
    String id = UUID.randomUUID().toString();
    String metadata = new ObjectMapper().writeValueAsString("{\"kind\":\"booking\",\"id\":\"" + id + "\"}");
    byte[] body = chargeSuccess(BOOKING_REF, 5000, metadata, "test");
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);

    service.handleWebhook(body, sign(body, SECRET));

    ArgumentCaptor<ConfirmedPayment> paid = ArgumentCaptor.forClass(ConfirmedPayment.class);
    verify(settlement).settle(paid.capture());
    assertThat(paid.getValue().kind()).isEqualTo("booking");
    assertThat(paid.getValue().recordId()).isEqualTo(id);
  }

  @Test
  void otherEventsAndUnsuccessfulChargesAreIgnored() throws Exception {
    byte[] transfer = "{\"event\":\"transfer.success\",\"data\":{}}".getBytes(StandardCharsets.UTF_8);
    assertThat(service.handleWebhook(transfer, sign(transfer, SECRET))).isEqualTo(Outcome.NOT_FOUND);

    byte[] failed =
        ("{\"event\":\"charge.success\",\"data\":{\"id\":1,\"status\":\"failed\",\"reference\":\"" + ORDER_REF + "\","
                + "\"amount\":100,\"currency\":\"ZAR\"}}")
            .getBytes(StandardCharsets.UTF_8);
    assertThat(service.handleWebhook(failed, sign(failed, SECRET))).isEqualTo(Outcome.NOT_FOUND);
    verify(settlement, never()).settle(any());
  }

  // ------------------------------------------------------------------ initialize

  @Test
  void orderCheckoutBuildsTheInitializeRequestWithoutEmailInTheCallback() {
    OrderEntity o = pendingOrder("150.50");
    o.onlinePaymentChannel = OnlinePaymentChannel.EFT;
    when(client.initialize(any()))
        .thenReturn(new PaystackClient.InitializeResult("https://checkout.paystack.com/abc", "x"));

    OnlineCheckoutSession session = service.initiateOrderCheckout(tenantId, o.id, "demo");

    ArgumentCaptor<PaystackClient.InitializeRequest> req = ArgumentCaptor.forClass(PaystackClient.InitializeRequest.class);
    verify(client).initialize(req.capture());
    PaystackClient.InitializeRequest r = req.getValue();
    assertThat(r.email()).isEqualTo("buyer+1@example.com");
    assertThat(r.amountCents()).isEqualTo(15050);
    assertThat(r.currency()).isEqualTo("ZAR");
    assertThat(r.reference()).matches("ORD[A-Z0-9]{16}").isEqualTo(o.onlinePaymentReference);
    assertThat(r.callbackUrl()).isEqualTo("https://shop.test/m/demo/payment/return?kind=order&id=" + o.id);
    assertThat(r.callbackUrl()).doesNotContain("email").doesNotContain("@");
    assertThat(r.channels()).containsExactly("eft");
    assertThat(r.metadata()).isEqualTo(Map.of("kind", "order", "id", o.id.toString()));
    assertThat(session.paymentId()).isEqualTo(o.id.toString());
    assertThat(session.paymentRedirectUrl()).isEqualTo("https://checkout.paystack.com/abc");
    verify(orders).save(o);
  }

  @Test
  void everyCheckoutAttemptGetsANewReference() {
    OrderEntity o = pendingOrder("10.00");
    when(client.initialize(any())).thenReturn(new PaystackClient.InitializeResult("https://checkout.paystack.com/a", "a"));

    service.initiateOrderCheckout(tenantId, o.id, "demo");
    String first = o.onlinePaymentReference;
    service.initiateOrderCheckout(tenantId, o.id, "demo");

    assertThat(o.onlinePaymentReference).isNotEqualTo(first);
    ArgumentCaptor<PaystackClient.InitializeRequest> req = ArgumentCaptor.forClass(PaystackClient.InitializeRequest.class);
    verify(client, times(2)).initialize(req.capture());
    assertThat(req.getAllValues().get(0).channels()).containsExactly("card");
  }

  @Test
  void checkoutIsRefusedWhenUnavailableOrNotAccepted() {
    OrderEntity o = pendingOrder("10.00");

    when(features.isEnabled(PlatformFeatureService.PAYSTACK)).thenReturn(false);
    assertThatThrownBy(() -> service.initiateOrderCheckout(tenantId, o.id, "demo"))
        .hasMessage("online_payment_unavailable");

    when(features.isEnabled(PlatformFeatureService.PAYSTACK)).thenReturn(true);
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.acceptCustomerOnline = false;
    when(shopSettings.findByTenantId(tenantId)).thenReturn(Optional.of(s));
    assertThatThrownBy(() -> service.initiateOrderCheckout(tenantId, o.id, "demo"))
        .hasMessage("online_payment_not_accepted");
    verify(client, never()).initialize(any());
  }

  @Test
  void anotherTenantsOrderIsNotFoundBeforeShopSettingsAreChecked() {
    OrderEntity o = pendingOrder("10.00");
    UUID otherTenant = UUID.randomUUID();
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.acceptCustomerOnline = false;
    when(shopSettings.findByTenantId(otherTenant)).thenReturn(Optional.of(s));

    assertThatThrownBy(() -> service.initiateOrderCheckout(otherTenant, o.id, "demo")).hasMessage("not_found");
  }

  @Test
  void checkoutRefusesOrdersThatAreNotPendingOnlineOrders() {
    OrderEntity o = pendingOrder("10.00");
    o.paymentMethod = OrderEntity.PaymentMethod.cash_store;
    assertThatThrownBy(() -> service.initiateOrderCheckout(tenantId, o.id, "demo")).hasMessage("not_online_order");

    o.paymentMethod = OrderEntity.PaymentMethod.online;
    o.status = OrderEntity.OrderStatus.paid;
    assertThatThrownBy(() -> service.initiateOrderCheckout(tenantId, o.id, "demo")).hasMessage("order_not_pending");
  }

  @Test
  void paystackFailureBecomesOnlinePaymentUnavailable() {
    OrderEntity o = pendingOrder("10.00");
    when(client.initialize(any())).thenThrow(new PaystackClient.PaystackException("Paystack initialize failed (HTTP 400)"));

    assertThatThrownBy(() -> service.initiateOrderCheckout(tenantId, o.id, "demo"))
        .hasMessage("online_payment_unavailable");
    // Paying an existing order later must not cancel it.
    verify(checkout, never()).cancelUnstartedOnlineOrder(any(), any());
  }

  @Test
  void newOrderIsCancelledWhenItsPaymentCannotStart() {
    OrderEntity o = pendingOrder("10.00");
    when(client.initialize(any())).thenThrow(new PaystackClient.PaystackException("down"));

    assertThatThrownBy(() -> service.startNewOrderCheckout(tenantId, o.id, "demo"))
        .hasMessage("online_payment_unavailable");
    verify(checkout).cancelUnstartedOnlineOrder(tenantId, o.id);
  }

  @Test
  void newBookingIsCancelledWhenItsPaymentCannotStart() {
    SalonBookingEntity b = pendingBooking();
    when(bookings.lockById(b.id)).thenReturn(Optional.of(b));
    when(client.initialize(any())).thenThrow(new PaystackClient.PaystackException("down"));

    assertThatThrownBy(() -> service.startNewBookingCheckout(tenantId, b.id, "demo"))
        .hasMessage("online_payment_unavailable");
    verify(salon).cancelUnstartedOnlineBooking(tenantId, b.id);
  }

  @Test
  void subscriptionCheckoutChargesThePlanFeeToTheOwnerEmail() {
    MerchantSubscriptionEntity sub = new MerchantSubscriptionEntity();
    sub.tenantId = tenantId;
    sub.planTier = TenantEntity.SubscriptionPlan.STANDARD;
    when(merchantSubscriptions.findByTenantId(tenantId)).thenReturn(Optional.of(sub));
    SubscriptionPlanPricingEntity pricing = new SubscriptionPlanPricingEntity();
    pricing.subscriptionFee = 199.0;
    when(plans.findByTier(TenantEntity.SubscriptionPlan.STANDARD)).thenReturn(Optional.of(pricing));
    MembershipEntity owner = new MembershipEntity();
    owner.userId = UUID.randomUUID();
    owner.tenantId = tenantId;
    owner.role = Role.MERCHANT_OWNER;
    owner.createdAt = Instant.now();
    when(memberships.findAllByTenantIdAndRole(tenantId, Role.MERCHANT_OWNER)).thenReturn(List.of(owner));
    UserEntity user = new UserEntity();
    user.email = "owner@shop.test";
    when(users.findById(owner.userId)).thenReturn(Optional.of(user));
    when(subscriptionPayments.save(any()))
        .thenAnswer(
            inv -> {
              SubscriptionOnlinePaymentEntity p = inv.getArgument(0);
              p.prePersist();
              return p;
            });
    when(client.initialize(any())).thenReturn(new PaystackClient.InitializeResult("https://checkout.paystack.com/s", "s"));

    OnlineCheckoutSession session = service.initiateSubscriptionCheckout(tenantId, "demo", OnlinePaymentChannel.CARD);

    ArgumentCaptor<SubscriptionOnlinePaymentEntity> saved = ArgumentCaptor.forClass(SubscriptionOnlinePaymentEntity.class);
    verify(subscriptionPayments).save(saved.capture());
    SubscriptionOnlinePaymentEntity p = saved.getValue();
    assertThat(p.amount).isEqualByComparingTo(new BigDecimal("199.00"));
    assertThat(p.status).isEqualTo(SubscriptionOnlinePaymentEntity.STATUS_PENDING);
    assertThat(p.onlinePaymentReference).matches("SUB[A-Z0-9]{16}");
    ArgumentCaptor<PaystackClient.InitializeRequest> req = ArgumentCaptor.forClass(PaystackClient.InitializeRequest.class);
    verify(client).initialize(req.capture());
    assertThat(req.getValue().email()).isEqualTo("owner@shop.test");
    assertThat(req.getValue().amountCents()).isEqualTo(19900);
    assertThat(req.getValue().callbackUrl())
        .isEqualTo("https://shop.test/payment/return?kind=subscription&paymentId=" + p.id + "&merchant=demo");
    assertThat(req.getValue().metadata()).containsEntry("kind", "subscription").containsEntry("id", p.id.toString());
    assertThat(session.paymentId()).isEqualTo(p.id.toString());
    assertThat(sub.onlinePaymentReference).isEqualTo(p.onlinePaymentReference);
  }

  @Test
  void subscriptionCheckoutWaitsForTheTrialToEnd() {
    MerchantSubscriptionEntity sub = new MerchantSubscriptionEntity();
    sub.planTier = TenantEntity.SubscriptionPlan.STANDARD;
    when(merchantSubscriptions.findByTenantId(tenantId)).thenReturn(Optional.of(sub));
    when(subscriptionService.isBlockingTrialForCheckout(tenantId)).thenReturn(true);

    assertThatThrownBy(() -> service.initiateSubscriptionCheckout(tenantId, "demo", OnlinePaymentChannel.CARD))
        .hasMessage("trial_still_active");
    verify(client, never()).initialize(any());
  }

  // ------------------------------------------------------------------ verify + settle

  @Test
  void verifyAnswerForAnotherReferenceIsNeverSettled() {
    UUID id = UUID.randomUUID();
    when(client.verify(ORDER_REF)).thenReturn(Optional.of(transaction(OLDER_ORDER_REF, "success", 1000, "order", id)));

    assertThat(service.verifyAndSettle(ORDER_REF, "order", id)).isEqualTo(VerifyResult.REJECTED);
    verify(settlement, never()).settle(any());
  }

  @Test
  void verifyRequiresThisRecordsMetadata() {
    UUID id = UUID.randomUUID();
    when(client.verify(ORDER_REF)).thenReturn(Optional.of(transaction(ORDER_REF, "success", 1000, "order", UUID.randomUUID())));

    assertThat(service.verifyAndSettle(ORDER_REF, "order", id)).isEqualTo(VerifyResult.REJECTED);
    verify(settlement, never()).settle(any());
  }

  @Test
  void verifyIsNeverAskedForForeignReferences() {
    assertThat(service.verifyAndSettle("T685312322670591", "order", UUID.randomUUID())).isEqualTo(VerifyResult.NOT_CHECKED);
    verify(client, never()).verify(anyString());
  }

  // ------------------------------------------------------------------ status (return page)

  @Test
  void statusNeedsEmailOrReference() {
    OrderEntity o = statusOrder(OrderEntity.OrderStatus.pending_payment);
    when(orders.findOneByTenantAndId(tenantId, o.id)).thenReturn(o);

    assertThatThrownBy(() -> service.orderStatus(tenantId, o.id, null, null)).hasMessage("email_or_reference_required");
    assertThatThrownBy(() -> service.orderStatus(tenantId, o.id, "someone@else.com", null)).hasMessage("email_mismatch");
    verify(client, never()).verify(anyString());
  }

  @Test
  void statusVerifiesAPendingPaymentAndReportsItPaid() {
    OrderEntity pending = statusOrder(OrderEntity.OrderStatus.pending_payment);
    OrderEntity paid = statusOrder(OrderEntity.OrderStatus.paid);
    paid.id = pending.id;
    when(orders.findOneByTenantAndId(tenantId, pending.id)).thenReturn(pending, paid);
    when(client.verify(ORDER_REF)).thenReturn(Optional.of(transaction(ORDER_REF, "success", 1000, "order", pending.id)));
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);

    Map<String, Object> status = service.orderStatus(tenantId, pending.id, "Buyer@Example.com ", null);

    assertThat(status).containsEntry("paid", true).containsEntry("cancelled", false).containsEntry("status", "paid");
    assertThat(status).containsOnlyKeys("kind", "id", "status", "paymentMethod", "onlinePaymentChannel", "paid", "cancelled");
  }

  @Test
  void statusWithTheStoredReferenceNeedsNoEmail() {
    OrderEntity pending = statusOrder(OrderEntity.OrderStatus.pending_payment);
    when(orders.findOneByTenantAndId(tenantId, pending.id)).thenReturn(pending);

    assertThat(service.orderStatus(tenantId, pending.id, null, ORDER_REF)).containsEntry("paid", false);
    verify(client).verify(ORDER_REF);
  }

  @Test
  void statusAcceptsAnEarlierAttemptReferenceFromTheLedger() {
    OrderEntity pending = statusOrder(OrderEntity.OrderStatus.pending_payment);
    OrderEntity paid = statusOrder(OrderEntity.OrderStatus.paid);
    paid.id = pending.id;
    when(orders.findOneByTenantAndId(tenantId, pending.id)).thenReturn(pending, paid);
    when(attempts.findByKindAndRecordIdAndReference(OnlinePaymentAttemptEntity.Kind.ORDER, pending.id, OLDER_ORDER_REF))
        .thenReturn(Optional.of(new OnlinePaymentAttemptEntity()));
    when(client.verify(OLDER_ORDER_REF))
        .thenReturn(Optional.of(transaction(OLDER_ORDER_REF, "success", 1000, "order", pending.id)));
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);

    Map<String, Object> status = service.orderStatus(tenantId, pending.id, null, OLDER_ORDER_REF);

    assertThat(status).containsEntry("paid", true);
    // The given reference is the one verified, not only the latest stored one.
    verify(client, never()).verify(ORDER_REF);
  }

  /** Minor 1: a reference that is not in the ledger for this order never reaches Paystack. */
  @Test
  void randomReferencesAreRefusedWithoutCallingPaystack() {
    OrderEntity pending = statusOrder(OrderEntity.OrderStatus.pending_payment);
    when(orders.findOneByTenantAndId(tenantId, pending.id)).thenReturn(pending);

    assertThatThrownBy(() -> service.orderStatus(tenantId, pending.id, null, OLDER_ORDER_REF))
        .hasMessage("reference_mismatch");
    assertThatThrownBy(() -> service.orderStatus(tenantId, pending.id, null, BOOKING_REF)).hasMessage("reference_mismatch");
    assertThatThrownBy(() -> service.orderStatus(tenantId, pending.id, null, "T685312322670591"))
        .hasMessage("reference_mismatch");
    verify(client, never()).verify(anyString());
    verify(settlement, never()).settle(any());
  }

  @Test
  void anyLedgerReferenceShowsASettledOrdersStatusWithoutAskingPaystack() {
    OrderEntity paid = statusOrder(OrderEntity.OrderStatus.paid);
    when(orders.findOneByTenantAndId(tenantId, paid.id)).thenReturn(paid);
    when(attempts.findByKindAndRecordIdAndReference(OnlinePaymentAttemptEntity.Kind.ORDER, paid.id, OLDER_ORDER_REF))
        .thenReturn(Optional.of(new OnlinePaymentAttemptEntity()));

    // An earlier attempt of this order (e.g. the tab the customer abandoned) still opens the status.
    assertThat(service.orderStatus(tenantId, paid.id, null, OLDER_ORDER_REF))
        .containsEntry("paid", true)
        .containsEntry("cancelled", false);
    assertThat(service.orderStatus(tenantId, paid.id, null, ORDER_REF)).containsEntry("paid", true);
    // A reference that is not this order's is still refused.
    assertThatThrownBy(() -> service.orderStatus(tenantId, paid.id, null, "ORDZZZZZZZZZZZZZZZZ"))
        .hasMessage("reference_mismatch");
    verify(client, never()).verify(anyString());
  }

  /** Major safety net: a paid attempt that fails to apply is recorded for refund and the webhook acknowledged. */
  @Test
  void webhookRecordsARefundWhenApplyingThePaymentFails() throws Exception {
    UUID attemptId = UUID.randomUUID();
    when(settlement.settle(any()))
        .thenThrow(
            new OnlinePaymentSettlementService.AttemptCannotBeAppliedException(
                attemptId, ORDER_REF, "order could not be marked paid", new IllegalArgumentException("x")));
    when(settlement.recordNeedsRefund(attemptId, ORDER_REF, "order could not be marked paid"))
        .thenReturn(Outcome.NEEDS_REFUND);
    byte[] body = chargeSuccess(ORDER_REF, 1000, "{}", "test");

    assertThat(service.handleWebhook(body, sign(body, SECRET))).isEqualTo(Outcome.NEEDS_REFUND);
    verify(settlement).recordNeedsRefund(attemptId, ORDER_REF, "order could not be marked paid");
  }

  /** Ledger lifecycle: every checkout start adds a PENDING attempt with the new reference and amount. */
  @Test
  void everyCheckoutStartIsRecordedInTheLedger() {
    OrderEntity o = pendingOrder("150.50");
    when(client.initialize(any())).thenReturn(new PaystackClient.InitializeResult("https://checkout.paystack.com/a", "a"));

    service.initiateOrderCheckout(tenantId, o.id, "demo");

    ArgumentCaptor<OnlinePaymentAttemptEntity> saved = ArgumentCaptor.forClass(OnlinePaymentAttemptEntity.class);
    verify(attempts).save(saved.capture());
    OnlinePaymentAttemptEntity a = saved.getValue();
    assertThat(a.kind).isEqualTo(OnlinePaymentAttemptEntity.Kind.ORDER);
    assertThat(a.recordId).isEqualTo(o.id);
    assertThat(a.tenantId).isEqualTo(tenantId);
    assertThat(a.reference).isEqualTo(o.onlinePaymentReference);
    assertThat(a.amountCents).isEqualTo(15050);
    assertThat(a.outcome).isEqualTo(OnlinePaymentAttemptEntity.Outcome.PENDING);
  }

  /** M2: an earlier attempt that was paid meanwhile is settled and the new attempt refused. */
  @Test
  void newAttemptIsRefusedWhenAnEarlierAttemptWasPaid() {
    OrderEntity o = pendingOrder("10.00");
    OnlinePaymentAttemptEntity earlier = new OnlinePaymentAttemptEntity();
    earlier.kind = OnlinePaymentAttemptEntity.Kind.ORDER;
    earlier.recordId = o.id;
    earlier.tenantId = tenantId;
    earlier.reference = OLDER_ORDER_REF;
    when(attempts.findByKindAndRecordIdAndOutcome(
            OnlinePaymentAttemptEntity.Kind.ORDER, o.id, OnlinePaymentAttemptEntity.Outcome.PENDING))
        .thenReturn(List.of(earlier));
    when(client.verify(OLDER_ORDER_REF))
        .thenReturn(Optional.of(transaction(OLDER_ORDER_REF, "success", 1000, "order", o.id)));
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);

    assertThatThrownBy(() -> service.initiateOrderCheckout(tenantId, o.id, "demo")).hasMessage("already_paid");
    verify(client, never()).initialize(any());
    verify(settlement).settle(any());
  }

  @Test
  void newAttemptStartsWhenEarlierAttemptsAreUnpaid() {
    OrderEntity o = pendingOrder("10.00");
    OnlinePaymentAttemptEntity earlier = new OnlinePaymentAttemptEntity();
    earlier.kind = OnlinePaymentAttemptEntity.Kind.ORDER;
    earlier.recordId = o.id;
    earlier.tenantId = tenantId;
    earlier.reference = OLDER_ORDER_REF;
    when(attempts.findByKindAndRecordIdAndOutcome(
            OnlinePaymentAttemptEntity.Kind.ORDER, o.id, OnlinePaymentAttemptEntity.Outcome.PENDING))
        .thenReturn(List.of(earlier));
    when(client.verify(OLDER_ORDER_REF))
        .thenReturn(Optional.of(transaction(OLDER_ORDER_REF, "abandoned", 1000, "order", o.id)));
    when(client.initialize(any())).thenReturn(new PaystackClient.InitializeResult("https://checkout.paystack.com/n", "n"));

    assertThat(service.initiateOrderCheckout(tenantId, o.id, "demo").paymentRedirectUrl())
        .isEqualTo("https://checkout.paystack.com/n");
  }

  @Test
  void statusReReadsTheOrderWhenItWasAlreadySettled() {
    OrderEntity stale = statusOrder(OrderEntity.OrderStatus.pending_payment);
    OrderEntity paid = statusOrder(OrderEntity.OrderStatus.paid);
    paid.id = stale.id;
    when(orders.findOneByTenantAndId(tenantId, stale.id)).thenReturn(stale, paid);
    when(client.verify(ORDER_REF)).thenReturn(Optional.of(transaction(ORDER_REF, "success", 1000, "order", stale.id)));
    when(settlement.settle(any())).thenReturn(Outcome.ALREADY_SETTLED);

    assertThat(service.orderStatus(tenantId, stale.id, "buyer@example.com", null)).containsEntry("paid", true);
  }

  @Test
  void statusVerifiesAnOrderCancelledByExpirySoALatePaymentRevivesIt() {
    OrderEntity expired = statusOrder(OrderEntity.OrderStatus.cancelled);
    expired.expiredAt = Instant.now();
    OrderEntity paid = statusOrder(OrderEntity.OrderStatus.paid);
    paid.id = expired.id;
    when(orders.findOneByTenantAndId(tenantId, expired.id)).thenReturn(expired, paid);
    when(client.verify(ORDER_REF)).thenReturn(Optional.of(transaction(ORDER_REF, "success", 1000, "order", expired.id)));
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);

    assertThat(service.orderStatus(tenantId, expired.id, "buyer@example.com", null)).containsEntry("paid", true);
    verify(settlement).settle(any());
  }

  @Test
  void statusDoesNotVerifyAnOrderCancelledByTheMerchant() {
    OrderEntity cancelled = statusOrder(OrderEntity.OrderStatus.cancelled);
    when(orders.findOneByTenantAndId(tenantId, cancelled.id)).thenReturn(cancelled);

    service.orderStatus(tenantId, cancelled.id, "buyer@example.com", null);
    verify(client, never()).verify(anyString());
  }

  @Test
  void statusReportsAnAbandonedCheckoutAsCancelled() {
    OrderEntity pending = statusOrder(OrderEntity.OrderStatus.pending_payment);
    when(orders.findOneByTenantAndId(tenantId, pending.id)).thenReturn(pending);
    when(client.verify(ORDER_REF)).thenReturn(Optional.of(transaction(ORDER_REF, "abandoned", 1000, "order", pending.id)));

    Map<String, Object> status = service.orderStatus(tenantId, pending.id, "buyer@example.com", null);

    assertThat(status).containsEntry("paid", false).containsEntry("cancelled", true);
    verify(settlement, never()).settle(any());
  }

  @Test
  void statusStaysPendingWhenPaystackCannotBeReached() {
    OrderEntity pending = statusOrder(OrderEntity.OrderStatus.pending_payment);
    when(orders.findOneByTenantAndId(tenantId, pending.id)).thenReturn(pending);
    when(client.verify(ORDER_REF)).thenThrow(new PaystackClient.PaystackException("Paystack could not be reached"));

    assertThat(service.orderStatus(tenantId, pending.id, "buyer@example.com", null))
        .containsEntry("paid", false)
        .containsEntry("cancelled", false);
  }

  @Test
  void bookingStatusVerifiesAPendingPayment() {
    SalonBookingEntity b = pendingBooking();
    b.onlinePaymentReference = BOOKING_REF;
    SalonBookingEntity confirmed = pendingBooking();
    confirmed.id = b.id;
    confirmed.status = SalonBookingEntity.Status.confirmed;
    when(bookings.findOneByTenantAndId(tenantId, b.id)).thenReturn(b, confirmed);
    when(client.verify(BOOKING_REF)).thenReturn(Optional.of(transaction(BOOKING_REF, "success", 5000, "booking", b.id)));
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);

    assertThat(service.bookingStatus(tenantId, b.id, "buyer@example.com", null))
        .containsEntry("paid", true)
        .containsEntry("kind", "booking");
  }

  // ------------------------------------------------------------------ subscription status

  @Test
  void subscriptionStatusVerifiesAPendingPaymentAndReportsItPaid() {
    SubscriptionOnlinePaymentEntity pending = subscriptionPayment(SubscriptionOnlinePaymentEntity.STATUS_PENDING);
    SubscriptionOnlinePaymentEntity done = subscriptionPayment(SubscriptionOnlinePaymentEntity.STATUS_COMPLETED);
    done.id = pending.id;
    when(subscriptionPayments.findById(pending.id)).thenReturn(Optional.of(pending), Optional.of(done));
    when(client.verify(SUB_REF)).thenReturn(Optional.of(transaction(SUB_REF, "success", 19900, "subscription", pending.id)));
    when(settlement.settle(any())).thenReturn(Outcome.SETTLED);

    Map<String, Object> status = service.subscriptionPaymentStatus(tenantId, pending.id, null);

    assertThat(status)
        .containsEntry("paymentId", pending.id.toString())
        .containsEntry("status", "completed")
        .containsEntry("paid", true)
        .containsEntry("cancelled", false);
  }

  @Test
  void subscriptionStatusOfAPaidPaymentDoesNotCallPaystack() {
    SubscriptionOnlinePaymentEntity done = subscriptionPayment(SubscriptionOnlinePaymentEntity.STATUS_COMPLETED);
    when(subscriptionPayments.findById(done.id)).thenReturn(Optional.of(done));

    assertThat(service.subscriptionPaymentStatus(tenantId, done.id, SUB_REF)).containsEntry("paid", true);
    verify(client, never()).verify(anyString());
  }

  @Test
  void subscriptionStatusChecksTheGivenReference() {
    SubscriptionOnlinePaymentEntity pending = subscriptionPayment(SubscriptionOnlinePaymentEntity.STATUS_PENDING);
    when(subscriptionPayments.findById(pending.id)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.subscriptionPaymentStatus(tenantId, pending.id, "SUBZZZZYYYYXXXXWWWW"))
        .hasMessage("reference_mismatch");
  }

  @Test
  void subscriptionStatusReportsAnAbandonedCheckoutAsCancelled() {
    SubscriptionOnlinePaymentEntity pending = subscriptionPayment(SubscriptionOnlinePaymentEntity.STATUS_PENDING);
    when(subscriptionPayments.findById(pending.id)).thenReturn(Optional.of(pending));
    when(client.verify(SUB_REF)).thenReturn(Optional.of(transaction(SUB_REF, "failed", 19900, "subscription", pending.id)));

    assertThat(service.subscriptionPaymentStatus(tenantId, pending.id, null))
        .containsEntry("paid", false)
        .containsEntry("cancelled", true)
        .containsEntry("status", "pending_online");
    verify(settlement, never()).settle(any());
  }

  @Test
  void subscriptionStatusStaysPendingWhenPaystackCannotBeReached() {
    SubscriptionOnlinePaymentEntity pending = subscriptionPayment(SubscriptionOnlinePaymentEntity.STATUS_PENDING);
    when(subscriptionPayments.findById(pending.id)).thenReturn(Optional.of(pending));
    when(client.verify(SUB_REF)).thenThrow(new PaystackClient.PaystackException("Paystack could not be reached"));

    assertThat(service.subscriptionPaymentStatus(tenantId, pending.id, null))
        .containsEntry("paid", false)
        .containsEntry("cancelled", false);
  }

  @Test
  void anotherTenantsSubscriptionPaymentIsNotFound() {
    SubscriptionOnlinePaymentEntity pending = subscriptionPayment(SubscriptionOnlinePaymentEntity.STATUS_PENDING);
    when(subscriptionPayments.findById(pending.id)).thenReturn(Optional.of(pending));

    assertThatThrownBy(() -> service.subscriptionPaymentStatus(UUID.randomUUID(), pending.id, null))
        .hasMessage("not_found");
    assertThatThrownBy(() -> service.subscriptionPaymentStatus(tenantId, UUID.randomUUID(), null))
        .hasMessage("not_found");
    verify(client, never()).verify(anyString());
  }

  // ------------------------------------------------------------------ helpers

  private OrderEntity pendingOrder(String total) {
    OrderEntity o = new OrderEntity();
    o.id = UUID.randomUUID();
    o.tenantId = tenantId;
    o.customerEmail = "buyer+1@example.com";
    o.paymentMethod = OrderEntity.PaymentMethod.online;
    o.status = OrderEntity.OrderStatus.pending_payment;
    o.totalZar = new BigDecimal(total);
    when(orders.lockById(o.id)).thenReturn(Optional.of(o));
    return o;
  }

  private OrderEntity statusOrder(OrderEntity.OrderStatus status) {
    OrderEntity o = new OrderEntity();
    o.id = UUID.randomUUID();
    o.tenantId = tenantId;
    o.customerEmail = "buyer@example.com";
    o.paymentMethod = OrderEntity.PaymentMethod.online;
    o.status = status;
    o.totalZar = new BigDecimal("10.00");
    o.onlinePaymentReference = ORDER_REF;
    return o;
  }

  private SalonBookingEntity pendingBooking() {
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.tenantId = tenantId;
    b.serviceId = UUID.randomUUID();
    b.customerEmail = "buyer@example.com";
    b.status = SalonBookingEntity.Status.pending;
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.online;
    b.amountZar = new BigDecimal("50.00");
    SalonServiceEntity svc = new SalonServiceEntity();
    svc.priceZar = new BigDecimal("50.00");
    when(salonServices.findByIdAndTenantId(b.serviceId, tenantId)).thenReturn(Optional.of(svc));
    return b;
  }

  private SubscriptionOnlinePaymentEntity subscriptionPayment(String status) {
    SubscriptionOnlinePaymentEntity p = new SubscriptionOnlinePaymentEntity();
    p.id = UUID.randomUUID();
    p.tenantId = tenantId;
    p.planTier = TenantEntity.SubscriptionPlan.STANDARD;
    p.amount = new BigDecimal("199.00");
    p.status = status;
    p.onlinePaymentChannel = OnlinePaymentChannel.CARD;
    p.onlinePaymentReference = SUB_REF;
    return p;
  }

  private static PaystackClient.Transaction transaction(
      String reference, String status, long amountCents, String kind, UUID id) {
    return new PaystackClient.Transaction(
        "302961", status, reference, amountCents, "ZAR", "card", "test", Map.of("kind", kind, "id", id.toString()));
  }

  private static byte[] chargeSuccess(String reference, long amount, String metadataJson, String domain) {
    return chargeSuccess(reference, amount, metadataJson, domain, "card");
  }

  private static byte[] chargeSuccess(String reference, long amount, String metadataJson, String domain, String channel) {
    String json =
        "{\"event\":\"charge.success\",\"data\":{\"id\":302961,\"domain\":\"" + domain + "\",\"status\":\"success\","
            + "\"reference\":\"" + reference + "\",\"amount\":" + amount + ",\"currency\":\"ZAR\","
            + "\"channel\":\"" + channel + "\",\"metadata\":" + metadataJson + "}}";
    return json.getBytes(StandardCharsets.UTF_8);
  }

  private static org.springframework.mock.env.MockEnvironment environment(String profile) {
    org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
    env.setActiveProfiles(profile);
    return env;
  }

  static String sign(byte[] body, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec(secret.isEmpty() ? new byte[] {0} : secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    return HexFormat.of().formatHex(mac.doFinal(body));
  }
}
