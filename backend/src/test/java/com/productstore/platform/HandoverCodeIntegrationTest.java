package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.OrderItemEntity;
import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.HandoverCodeBackfill;
import com.productstore.platform.services.MerchantSubscriptionService;
import com.productstore.platform.services.auth.JwtService;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Handover code (CTO decision 2026-11): every order and booking has a code; staff must enter it to hand an order
 * over (collection or delivery) and to start a salon service. For cash it also records the payment; for online
 * payments it only authorises the handover. Wrong guesses are limited per order/booking.
 */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HandoverCodeIntegrationTest {

  private static final String SLUG = "handover-code-shop";
  private static final String CODE = "357913";
  private static final String WRONG = "100000";
  private static final String CUSTOMER = "handover-customer@test.local";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TenantRepository tenants;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired EmployeeRepository employees;
  @Autowired ShopSettingsRepository shopSettings;
  @Autowired MerchantSubscriptionRepository subscriptionRepository;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired ProductRepository products;
  @Autowired OrderRepository orders;
  @Autowired OrderItemRepository orderItems;
  @Autowired SalonServiceRepository services;
  @Autowired SalonBookingRepository bookings;
  @Autowired HandoverCodeBackfill backfill;
  @Autowired JwtService jwtService;
  @Autowired PasswordHasher passwordHasher;

  UUID tenantId;
  UUID productId;
  UUID serviceId;
  String ownerToken;

  @BeforeEach
  void seed() {
    cleanup();
    Instant now = Instant.now();
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = SLUG;
    t.name = "Handover Shop";
    t.modulesJson = "{}";
    t.createdAt = now;
    tenants.save(t);
    tenantId = t.id;
    shopSettings.save(settings(now));
    subscriptions.forceActivatePlan(tenantId, TenantEntity.SubscriptionPlan.STANDARD);

    UserEntity owner = new UserEntity();
    owner.id = UUID.randomUUID();
    owner.email = "owner-handover@test.local";
    owner.passwordHash = passwordHasher.hash("Owner@123456");
    owner.createdAt = now;
    users.save(owner);
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = owner.id;
    m.tenantId = tenantId;
    m.role = Role.MERCHANT_OWNER;
    m.createdAt = now;
    memberships.save(m);
    ownerToken = jwtService.mintToken(owner.id, owner.email, List.of(Role.MERCHANT_OWNER), tenantId, SLUG);

    ProductEntity p = new ProductEntity();
    p.id = UUID.randomUUID();
    p.tenantId = tenantId;
    p.name = "Widget";
    p.category = "Goods";
    p.priceZar = new BigDecimal("25.00");
    p.imageUrl = "";
    p.imagePath = "";
    p.stock = 5;
    p.createdAt = now;
    products.save(p);
    productId = p.id;

    SalonServiceEntity s = new SalonServiceEntity();
    s.id = UUID.randomUUID();
    s.tenantId = tenantId;
    s.name = "Haircut";
    s.description = "";
    s.durationMinutes = 60;
    s.priceZar = new BigDecimal("150.00");
    s.active = true;
    s.createdAt = now;
    services.save(s);
    serviceId = s.id;
  }

  @AfterEach
  void cleanup() {
    tenants.findBySlug(SLUG).ifPresent(t -> {
      orderItems.findAll().stream().filter(i -> t.id.equals(i.tenantId)).forEach(orderItems::delete);
      orders.findAllByTenant(t.id).forEach(orders::delete);
      bookings.findByTenantIdOrderByStartAtDesc(t.id).forEach(bookings::delete);
      services.findByTenantIdOrderByCreatedAtDesc(t.id).forEach(services::delete);
      products.findAll().stream().filter(p -> t.id.equals(p.tenantId)).forEach(products::delete);
      employees.findAll().stream().filter(e -> t.id.equals(e.tenantId)).forEach(employees::delete);
      shopSettings.findByTenantId(t.id).ifPresent(shopSettings::delete);
      subscriptionRepository.findAll().stream().filter(x -> t.id.equals(x.tenantId)).forEach(subscriptionRepository::delete);
      List<MembershipEntity> mine = memberships.findAll().stream().filter(x -> t.id.equals(x.tenantId)).toList();
      memberships.deleteAll(mine);
      mine.forEach(x -> users.deleteById(x.userId));
      tenants.delete(t);
    });
  }

  // ------------------------------------------------------------------ orders

  @Test
  void onlineOrderCollectionNeedsTheRightCodeAndLeavesThePaymentAlone() throws Exception {
    OrderEntity o = order(OrderEntity.PaymentMethod.online, OrderEntity.OrderStatus.paid, OrderEntity.DeliveryType.pickup);
    Instant confirmedAt = orders.findById(o.id).orElseThrow().paymentConfirmedAt;

    fulfil(ownerToken, o.id, "completed", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("handover_code_required"));
    fulfil(ownerToken, o.id, "completed", WRONG)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_handover_code"));
    assertThat(fulfilment(o.id)).isEqualTo(OrderEntity.FulfillmentStatus.processing);

    fulfil(ownerToken, o.id, "completed", " " + CODE + " ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fulfillmentStatus").value("completed"));

    OrderEntity after = orders.findById(o.id).orElseThrow();
    assertThat(after.status).isEqualTo(OrderEntity.OrderStatus.paid);
    assertThat(after.paymentConfirmedAt).isEqualTo(confirmedAt);
    assertThat(stock()).isEqualTo(5); // stock was taken when the online payment arrived, not again now
  }

  @Test
  void deliveryHandoverNeedsTheCodeButOutForDeliveryDoesNot() throws Exception {
    OrderEntity o =
        order(OrderEntity.PaymentMethod.online, OrderEntity.OrderStatus.paid, OrderEntity.DeliveryType.delivery);

    fulfil(ownerToken, o.id, "ready", null).andExpect(status().isOk());
    fulfil(ownerToken, o.id, "completed", WRONG).andExpect(status().isBadRequest());
    assertThat(fulfilment(o.id)).isEqualTo(OrderEntity.FulfillmentStatus.ready);

    fulfil(ownerToken, o.id, "completed", CODE).andExpect(status().isOk());
    assertThat(fulfilment(o.id)).isEqualTo(OrderEntity.FulfillmentStatus.completed);
  }

  @Test
  void unpaidCashOrderHandedOverWithTheCodeIsMarkedPaid() throws Exception {
    OrderEntity o =
        order(OrderEntity.PaymentMethod.cash_store, OrderEntity.OrderStatus.pending_payment, OrderEntity.DeliveryType.delivery);

    fulfil(ownerToken, o.id, "completed", WRONG)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_handover_code"));
    OrderEntity refused = orders.findById(o.id).orElseThrow();
    assertThat(refused.status).isEqualTo(OrderEntity.OrderStatus.pending_payment);
    assertThat(refused.paymentConfirmedAt).isNull();
    assertThat(stock()).isEqualTo(5);

    fulfil(ownerToken, o.id, "completed", CODE).andExpect(status().isOk());

    OrderEntity paid = orders.findById(o.id).orElseThrow();
    assertThat(paid.status).isEqualTo(OrderEntity.OrderStatus.paid);
    assertThat(paid.paymentConfirmedAt).isNotNull();
    assertThat(paid.fulfillmentStatus).isEqualTo(OrderEntity.FulfillmentStatus.completed);
    assertThat(stock()).isEqualTo(4);
  }

  @Test
  void cashCodeStillConfirmsPaymentAndThenHandsTheOrderOver() throws Exception {
    OrderEntity o =
        order(OrderEntity.PaymentMethod.cash_store, OrderEntity.OrderStatus.pending_payment, OrderEntity.DeliveryType.pickup);

    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + o.id + "/confirm-payment")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cashCode\":\"" + CODE + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));
    assertThat(orders.findById(o.id).orElseThrow().status).isEqualTo(OrderEntity.OrderStatus.paid);
    assertThat(stock()).isEqualTo(4);

    fulfil(ownerToken, o.id, "completed", null).andExpect(status().isBadRequest());
    fulfil(ownerToken, o.id, "completed", CODE).andExpect(status().isOk());
    assertThat(stock()).isEqualTo(4);
  }

  @Test
  void unpaidOnlineOrderCannotBeHandedOverEvenWithTheCode() throws Exception {
    OrderEntity o =
        order(OrderEntity.PaymentMethod.online, OrderEntity.OrderStatus.pending_payment, OrderEntity.DeliveryType.pickup);

    fulfil(ownerToken, o.id, "completed", CODE)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("order_not_paid"));
    assertThat(orders.findById(o.id).orElseThrow().status).isEqualTo(OrderEntity.OrderStatus.pending_payment);
  }

  @Test
  void staffWithoutPaymentPermissionHandOverPaidOrdersButCannotTakeCash() throws Exception {
    String staffToken = staffWith("handover-staff@test.local", "ORDERS", "BOOKINGS");
    OrderEntity cash =
        order(OrderEntity.PaymentMethod.cash_store, OrderEntity.OrderStatus.pending_payment, OrderEntity.DeliveryType.pickup);
    OrderEntity online =
        order(OrderEntity.PaymentMethod.online, OrderEntity.OrderStatus.paid, OrderEntity.DeliveryType.pickup);

    fulfil(staffToken, cash.id, "completed", CODE)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("permission_denied"));
    assertThat(orders.findById(cash.id).orElseThrow().status).isEqualTo(OrderEntity.OrderStatus.pending_payment);

    fulfil(staffToken, online.id, "completed", CODE).andExpect(status().isOk());
  }

  @Test
  void anotherStoresOrderIsNotFound() throws Exception {
    OrderEntity o = order(OrderEntity.PaymentMethod.online, OrderEntity.OrderStatus.paid, OrderEntity.DeliveryType.pickup);
    o.tenantId = UUID.randomUUID();
    orders.save(o);

    fulfil(ownerToken, o.id, "completed", CODE)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }

  @Test
  void wrongOrderCodesLockTheOrderForCashConfirmationToo() throws Exception {
    OrderEntity o =
        order(OrderEntity.PaymentMethod.cash_store, OrderEntity.OrderStatus.pending_payment, OrderEntity.DeliveryType.pickup);

    for (int i = 0; i < 5; i++) {
      fulfil(ownerToken, o.id, "completed", WRONG).andExpect(status().isBadRequest());
    }
    // Locked: even the right code is refused, on handover and on cash confirmation alike.
    fulfil(ownerToken, o.id, "completed", CODE).andExpect(status().isTooManyRequests());
    mvc.perform(
            post("/api/m/" + SLUG + "/admin/orders/" + o.id + "/confirm-payment")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cashCode\":\"" + CODE + "\"}"))
        .andExpect(status().isTooManyRequests());
    assertThat(orders.findById(o.id).orElseThrow().status).isEqualTo(OrderEntity.OrderStatus.pending_payment);
  }

  @Test
  void customerGetsTheCodeAtCheckoutAndOnTheTrackPageUntilHandover() throws Exception {
    Map<String, Object> req = new LinkedHashMap<>();
    req.put("customerName", "Pat Client");
    req.put("customerEmail", CUSTOMER);
    req.put("customerPhone", "0820000000");
    req.put("deliveryType", "pickup");
    req.put("paymentMethod", "cash_store");
    req.put("items", List.of(Map.of("product_id", productId.toString(), "quantity", 1)));
    JsonNode placed =
        read(mvc.perform(
                post("/api/public/m/" + SLUG + "/checkout/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isCreated()));
    String code = placed.get("handoverCode").asText();
    assertThat(code).matches("[0-9]{6}");
    assertThat(placed.get("cashPaymentCode").asText()).isEqualTo(code);
    assertThat(placed.get("needsCashPaymentCode").asBoolean()).isTrue();
    String orderId = placed.get("orderId").asText();

    JsonNode tracked = track(orderId);
    assertThat(tracked.get("handoverCode").asText()).isEqualTo(code);
    assertThat(tracked.get("cashPaymentCode").asText()).isEqualTo(code);

    fulfil(ownerToken, UUID.fromString(orderId), "completed", code).andExpect(status().isOk());
    JsonNode done = track(orderId);
    assertThat(done.has("handoverCode")).isFalse();
    assertThat(done.has("cashPaymentCode")).isFalse();
  }

  // ---------------------------------------------------------------- bookings

  @Test
  void onlineBookingStartNeedsTheRightCodeAndLeavesThePaymentAlone() throws Exception {
    SalonBookingEntity b = booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.confirmed);
    Instant paidAt = bookings.findById(b.id).orElseThrow().completedAt;

    setStatus(ownerToken, b.id, "in_progress", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("handover_code_required"));
    setStatus(ownerToken, b.id, "in_progress", WRONG)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_handover_code"));
    setStatus(ownerToken, b.id, "completed", null).andExpect(status().isBadRequest());
    assertThat(bookings.findById(b.id).orElseThrow().status).isEqualTo(SalonBookingEntity.Status.confirmed);

    setStatus(ownerToken, b.id, "in_progress", CODE).andExpect(status().isOk());

    SalonBookingEntity started = bookings.findById(b.id).orElseThrow();
    assertThat(started.status).isEqualTo(SalonBookingEntity.Status.in_progress);
    assertThat(started.completedAt).isEqualTo(paidAt);
    assertThat(started.paymentVerificationState)
        .isEqualTo(SalonBookingEntity.PaymentVerificationState.not_applicable);

    // Finishing a started service needs no second code.
    setStatus(ownerToken, b.id, "completed", null).andExpect(status().isOk());
  }

  @Test
  void unpaidCashBookingStartedWithTheCodeIsMarkedPaid() throws Exception {
    SalonBookingEntity b = booking(SalonBookingEntity.ClientPaymentMethod.cash_store, SalonBookingEntity.Status.pending);

    setStatus(ownerToken, b.id, "in_progress", WRONG).andExpect(status().isBadRequest());
    assertThat(bookings.findById(b.id).orElseThrow().completedAt).isNull();

    setStatus(ownerToken, b.id, "in_progress", CODE).andExpect(status().isOk());

    SalonBookingEntity started = bookings.findById(b.id).orElseThrow();
    assertThat(started.status).isEqualTo(SalonBookingEntity.Status.in_progress);
    assertThat(started.completedAt).isNotNull();
  }

  @Test
  void wrongBookingCodesLockTheBookingForCashConfirmationToo() throws Exception {
    SalonBookingEntity b = booking(SalonBookingEntity.ClientPaymentMethod.cash_store, SalonBookingEntity.Status.pending);

    for (int i = 0; i < 5; i++) {
      setStatus(ownerToken, b.id, "in_progress", WRONG).andExpect(status().isBadRequest());
    }
    setStatus(ownerToken, b.id, "in_progress", CODE).andExpect(status().isTooManyRequests());
    mvc.perform(
            post("/api/m/" + SLUG + "/admin/salon/bookings/" + b.id + "/confirm-cash")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + CODE + "\"}"))
        .andExpect(status().isTooManyRequests());
    assertThat(bookings.findById(b.id).orElseThrow().status).isEqualTo(SalonBookingEntity.Status.pending);
  }

  @Test
  void customerSeesTheBookingCodeUntilTheServiceStarts() throws Exception {
    SalonBookingEntity b = booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.confirmed);

    JsonNode before = lookupBooking(b.id);
    assertThat(before.get("handoverCode").asText()).isEqualTo(CODE);
    assertThat(before.has("cashPaymentCode")).isFalse(); // online: nothing to pay in store

    setStatus(ownerToken, b.id, "in_progress", CODE).andExpect(status().isOk());
    assertThat(lookupBooking(b.id).has("handoverCode")).isFalse();
  }

  // ---------------------------------------------------------------- backfill

  @Test
  void backfillGivesCodesToInFlightRowsOnlyAndIsIdempotent() {
    OrderEntity pending = orderWithoutCode(OrderEntity.OrderStatus.pending_payment, null);
    OrderEntity processing = orderWithoutCode(OrderEntity.OrderStatus.paid, null);
    OrderEntity ready = orderWithoutCode(OrderEntity.OrderStatus.paid, OrderEntity.FulfillmentStatus.ready);
    OrderEntity done = orderWithoutCode(OrderEntity.OrderStatus.paid, OrderEntity.FulfillmentStatus.completed);
    OrderEntity cancelled = orderWithoutCode(OrderEntity.OrderStatus.cancelled, null);
    OrderEntity keeps = order(OrderEntity.PaymentMethod.cash_store, OrderEntity.OrderStatus.pending_payment,
        OrderEntity.DeliveryType.pickup);
    SalonBookingEntity bPending = bookingWithoutCode(SalonBookingEntity.Status.pending);
    SalonBookingEntity bConfirmed = bookingWithoutCode(SalonBookingEntity.Status.confirmed);
    SalonBookingEntity bStarted = bookingWithoutCode(SalonBookingEntity.Status.in_progress);
    SalonBookingEntity bCompleted = bookingWithoutCode(SalonBookingEntity.Status.completed);
    SalonBookingEntity bCancelled = bookingWithoutCode(SalonBookingEntity.Status.cancelled);

    HandoverCodeBackfill.Result first = backfill.backfill();
    assertThat(first.orders()).isGreaterThanOrEqualTo(3);
    assertThat(first.bookings()).isGreaterThanOrEqualTo(2);

    for (OrderEntity o : List.of(pending, processing, ready)) {
      assertThat(orders.findById(o.id).orElseThrow().handoverCode).matches("[0-9]{6}");
    }
    for (OrderEntity o : List.of(done, cancelled)) {
      assertThat(orders.findById(o.id).orElseThrow().handoverCode).isNull();
    }
    assertThat(orders.findById(keeps.id).orElseThrow().handoverCode).isEqualTo(CODE);
    for (SalonBookingEntity b : List.of(bPending, bConfirmed)) {
      assertThat(bookings.findById(b.id).orElseThrow().handoverCode).matches("[0-9]{6}");
    }
    for (SalonBookingEntity b : List.of(bStarted, bCompleted, bCancelled)) {
      assertThat(bookings.findById(b.id).orElseThrow().handoverCode).isNull();
    }

    String issued = orders.findById(pending.id).orElseThrow().handoverCode;
    assertThat(backfill.backfill()).isEqualTo(new HandoverCodeBackfill.Result(0, 0));
    assertThat(orders.findById(pending.id).orElseThrow().handoverCode).isEqualTo(issued);
  }

  // ----------------------------------------------------------------- helpers

  private ResultActions fulfil(String token, UUID orderId, String status, String code) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", status);
    if (code != null) body.put("handoverCode", code);
    return mvc.perform(
        post("/api/m/" + SLUG + "/admin/orders/" + orderId + "/fulfillment")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private ResultActions setStatus(String token, UUID bookingId, String status, String code) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", status);
    if (code != null) body.put("code", code);
    return mvc.perform(
        post("/api/m/" + SLUG + "/admin/salon/bookings/" + bookingId + "/status")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  private JsonNode track(String orderId) throws Exception {
    return read(
        mvc.perform(get("/api/public/m/" + SLUG + "/checkout/orders/" + orderId).param("customerEmail", CUSTOMER))
            .andExpect(status().isOk()));
  }

  private JsonNode lookupBooking(UUID bookingId) throws Exception {
    return read(
        mvc.perform(get("/api/public/m/" + SLUG + "/salon/bookings/" + bookingId).param("customerEmail", CUSTOMER))
            .andExpect(status().isOk()));
  }

  private JsonNode read(ResultActions r) throws Exception {
    return json.readTree(r.andReturn().getResponse().getContentAsString());
  }

  private OrderEntity.FulfillmentStatus fulfilment(UUID orderId) {
    return OrderEntity.effectiveFulfillment(orders.findById(orderId).orElseThrow());
  }

  private int stock() {
    return products.findById(productId).orElseThrow().stock;
  }

  private OrderEntity order(
      OrderEntity.PaymentMethod method, OrderEntity.OrderStatus status, OrderEntity.DeliveryType delivery) {
    Instant now = Instant.now();
    OrderEntity o = new OrderEntity();
    o.id = UUID.randomUUID();
    o.tenantId = tenantId;
    o.createdAt = now;
    o.customerName = "Pat Client";
    o.customerEmail = CUSTOMER;
    o.customerPhone = "0820000000";
    o.deliveryType = delivery;
    o.deliveryAddress = delivery == OrderEntity.DeliveryType.delivery ? "1 Long Street, Cape Town" : null;
    o.deliveryFeeZar = BigDecimal.ZERO;
    o.paymentMethod = method;
    o.status = status;
    o.paymentVerificationState = OrderEntity.PaymentVerificationState.not_applicable;
    if (status == OrderEntity.OrderStatus.paid) {
      o.fulfillmentStatus = OrderEntity.FulfillmentStatus.processing;
      o.paymentConfirmedAt = now.minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
    }
    o.handoverCode = CODE;
    o.subtotalZar = new BigDecimal("25.00");
    o.totalZar = new BigDecimal("25.00");
    orders.save(o);

    OrderItemEntity line = new OrderItemEntity();
    line.id = UUID.randomUUID();
    line.tenantId = tenantId;
    line.orderId = o.id;
    line.productId = productId;
    line.quantity = 1;
    line.unitPriceZar = new BigDecimal("25.00");
    line.lineTotalZar = new BigDecimal("25.00");
    orderItems.save(line);
    return o;
  }

  private OrderEntity orderWithoutCode(OrderEntity.OrderStatus status, OrderEntity.FulfillmentStatus fulfillment) {
    OrderEntity o = order(OrderEntity.PaymentMethod.online, status, OrderEntity.DeliveryType.pickup);
    o.fulfillmentStatus = fulfillment;
    if (status == OrderEntity.OrderStatus.cancelled) o.cancelledAt = Instant.now();
    o.handoverCode = null;
    return orders.save(o);
  }

  private SalonBookingEntity booking(SalonBookingEntity.ClientPaymentMethod method, SalonBookingEntity.Status status) {
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.tenantId = tenantId;
    b.serviceId = serviceId;
    b.customerName = "Pat Client";
    b.customerPhone = "0820000000";
    b.customerEmail = CUSTOMER;
    b.startAt = Instant.now().plus(1, ChronoUnit.HOURS);
    b.endAt = b.startAt.plus(1, ChronoUnit.HOURS);
    b.status = status;
    b.clientPaymentMethod = method;
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.not_applicable;
    b.amountZar = new BigDecimal("150.00");
    if (status != SalonBookingEntity.Status.pending) {
      b.completedAt = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
    }
    b.handoverCode = CODE;
    b.createdAt = Instant.now();
    return bookings.save(b);
  }

  private SalonBookingEntity bookingWithoutCode(SalonBookingEntity.Status status) {
    SalonBookingEntity b = booking(SalonBookingEntity.ClientPaymentMethod.online, status);
    b.handoverCode = null;
    return bookings.save(b);
  }

  /** A logged-in team member holding exactly {@code permissions}. */
  private String staffWith(String email, String... permissions) throws Exception {
    String created =
        mvc.perform(
                post("/api/m/" + SLUG + "/admin/team")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"" + email + "\",\"password\":\"Staff@123456\",\"displayName\":\"Staff\","
                            + "\"role\":\"STAFF\",\"payMethod\":\"PER_SERVICE\",\"payRate\":100,\"bonusPercentage\":0}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String employeeId = json.readTree(created).path("member").path("id").asText();
    mvc.perform(
            put("/api/m/" + SLUG + "/admin/team/" + employeeId + "/permissions")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("permissions", List.of(permissions)))))
        .andExpect(status().isOk());
    String login =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"Staff@123456\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(login).get("token").asText();
  }

  private ShopSettingsEntity settings(Instant now) {
    ShopSettingsEntity s = new ShopSettingsEntity();
    s.id = UUID.randomUUID();
    s.tenantId = tenantId;
    s.deliveryFeeMode = "standard";
    s.deliveryFeeFlatZar = BigDecimal.ZERO;
    s.deliveryFeePerKmZar = BigDecimal.ZERO;
    s.eftBankInstructions = "";
    s.bankName = "";
    s.bankAccountHolder = "";
    s.bankAccountNumber = "";
    s.bankBranchCode = "";
    s.storeName = "Handover";
    s.contactEmail = "shop@handover.test";
    s.contactPhone = "000";
    s.contactAddress = "";
    s.contactNotes = "";
    s.storeLogoUrl = "";
    s.storeHeroUrl = "";
    s.shopType = "salon_and_store";
    s.openingHoursJson = "[]";
    s.acceptCustomerOnline = true;
    s.acceptCustomerEft = false;
    s.acceptCustomerCash = true;
    s.createdAt = now;
    s.updatedAt = now;
    return s;
  }
}
