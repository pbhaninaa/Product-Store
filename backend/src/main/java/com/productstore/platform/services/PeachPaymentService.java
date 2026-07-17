package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.config.PeachProperties;
import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.PeachPaymentMethod;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.SubscriptionPlanPricingEntity;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.SubscriptionPlanPricingRepository;
import com.productstore.platform.util.PeachSignatureUtil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Peach Payments Hosted Checkout V2 for product orders and salon bookings (card + instant EFT).
 */
@Service
public class PeachPaymentService {
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
  private static final long TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS = 30;

  private final PeachProperties peachProperties;
  private final OrderRepository orders;
  private final SalonBookingRepository bookings;
  private final SalonServiceRepository salonServices;
  private final ShopSettingsRepository shopSettings;
  private final MerchantSubscriptionRepository merchantSubscriptions;
  private final SubscriptionPlanPricingRepository subscriptionPlans;
  private final CheckoutService checkoutService;
  private final SalonBookingService salonBookingService;
  private final MerchantSubscriptionService merchantSubscriptionService;
  private final String frontendBaseUrl;
  private final String publicBaseUrl;
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private volatile String cachedAccessToken;
  private volatile Instant cachedAccessTokenExpiry = Instant.EPOCH;

  public PeachPaymentService(
      PeachProperties peachProperties,
      OrderRepository orders,
      SalonBookingRepository bookings,
      SalonServiceRepository salonServices,
      ShopSettingsRepository shopSettings,
      MerchantSubscriptionRepository merchantSubscriptions,
      SubscriptionPlanPricingRepository subscriptionPlans,
      @Lazy CheckoutService checkoutService,
      @Lazy SalonBookingService salonBookingService,
      @Lazy MerchantSubscriptionService merchantSubscriptionService,
      @Value("${app.frontend-base-url:http://localhost:8085}") String frontendBaseUrl,
      @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
    this.peachProperties = peachProperties;
    this.orders = orders;
    this.bookings = bookings;
    this.salonServices = salonServices;
    this.shopSettings = shopSettings;
    this.merchantSubscriptions = merchantSubscriptions;
    this.subscriptionPlans = subscriptionPlans;
    this.checkoutService = checkoutService;
    this.salonBookingService = salonBookingService;
    this.merchantSubscriptionService = merchantSubscriptionService;
    this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
    this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
  }

  public boolean isPlatformConfigured() {
    return peachProperties.isConfigured();
  }

  public record PeachCheckoutSession(String checkoutId, String redirectUrl) {}

  @Transactional
  public PeachCheckoutSession initiateOrderCheckout(UUID tenantId, UUID orderId, String merchantSlug) {
    requireConfigured();
    requireShopAcceptsPeach(tenantId);
    OrderEntity o = orders.findOneByTenantAndId(tenantId, orderId);
    if (o == null) throw new IllegalArgumentException("not_found");
    if (o.paymentMethod != OrderEntity.PaymentMethod.peach) {
      throw new IllegalArgumentException("not_peach_order");
    }
    if (o.status != OrderEntity.OrderStatus.pending_payment) {
      throw new IllegalArgumentException("order_not_pending");
    }
    if (o.cancelledAt != null) throw new IllegalArgumentException("order_cancelled");

    if (o.peachMerchantTransactionId == null || o.peachMerchantTransactionId.isBlank()) {
      o.peachMerchantTransactionId = newMerchantTransactionId();
    }
    BigDecimal amount = o.totalZar == null ? BigDecimal.ZERO : o.totalZar.setScale(2, RoundingMode.HALF_UP);
    String returnPath =
        "/m/"
            + merchantSlug
            + "/peach/return?kind=order&id="
            + o.id
            + "&email="
            + urlEncode(o.customerEmail);
    String shopperResultUrl = shopperResultUrl(returnPath);
    String notificationUrl = trimSlash(publicBaseUrl) + "/api/public/peach/webhook";

    PeachCheckoutSession session =
        createHostedCheckout(
            amount,
            o.peachMerchantTransactionId,
            shopperResultUrl,
            notificationUrl,
            o.peachPaymentMethod);
    o.peachCheckoutId = session.checkoutId();
    orders.save(o);
    return session;
  }

  @Transactional
  public PeachCheckoutSession initiateBookingCheckout(UUID tenantId, UUID bookingId, String merchantSlug) {
    requireConfigured();
    requireShopAcceptsPeach(tenantId);
    SalonBookingEntity b = bookings.findOneByTenantAndId(tenantId, bookingId);
    if (b == null) throw new IllegalArgumentException("not_found");
    if (b.clientPaymentMethod != SalonBookingEntity.ClientPaymentMethod.peach) {
      throw new IllegalArgumentException("not_peach_booking");
    }
    if (b.status != SalonBookingEntity.Status.pending) {
      throw new IllegalArgumentException("booking_not_pending");
    }

    SalonServiceEntity svc =
        salonServices
            .findByIdAndTenantId(b.serviceId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("service_not_found"));
    BigDecimal amount =
        svc.priceZar == null ? BigDecimal.ZERO : svc.priceZar.setScale(2, RoundingMode.HALF_UP);
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("invalid_amount");
    }

    if (b.peachMerchantTransactionId == null || b.peachMerchantTransactionId.isBlank()) {
      b.peachMerchantTransactionId = newMerchantTransactionId();
    }
    String returnPath =
        "/m/"
            + merchantSlug
            + "/peach/return?kind=booking&id="
            + b.id
            + "&email="
            + urlEncode(b.customerEmail);
    String shopperResultUrl = shopperResultUrl(returnPath);
    String notificationUrl = trimSlash(publicBaseUrl) + "/api/public/peach/webhook";

    PeachCheckoutSession session =
        createHostedCheckout(
            amount,
            b.peachMerchantTransactionId,
            shopperResultUrl,
            notificationUrl,
            b.peachPaymentMethod);
    b.peachCheckoutId = session.checkoutId();
    bookings.save(b);
    return session;
  }

  /** Merchant subscription billing — platform Peach account, one-off payment per billing period. */
  @Transactional
  public PeachCheckoutSession initiateSubscriptionCheckout(
      UUID tenantId, String merchantSlug, PeachPaymentMethod peachPaymentMethod) {
    requireConfigured();
    if (peachPaymentMethod == null) {
      throw new IllegalArgumentException("peach_payment_method_required");
    }
    MerchantSubscriptionEntity sub =
        merchantSubscriptions
            .findByTenantId(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("subscription_not_found"));
    if (sub.planTier == null) {
      throw new IllegalArgumentException("select_plan_first");
    }
    boolean valid = merchantSubscriptionService.hasEffectiveSubscription(tenantId);
    boolean upgrading = merchantSubscriptionService.hasPendingUpgrade(tenantId);
    if (valid && !upgrading) {
      throw new IllegalArgumentException("already_active_for_current_plan");
    }
    SubscriptionPlanPricingEntity pricing =
        subscriptionPlans.findByTier(sub.planTier).orElseThrow(() -> new IllegalArgumentException("plan_missing"));
    BigDecimal amount = BigDecimal.valueOf(pricing.subscriptionFee).setScale(2, RoundingMode.HALF_UP);
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("invalid_amount");
    }

    if (sub.peachMerchantTransactionId == null || sub.peachMerchantTransactionId.isBlank()) {
      sub.peachMerchantTransactionId = newMerchantTransactionId();
    }
    sub.peachPaymentMethod = peachPaymentMethod;
    String shopperResultUrl =
        shopperResultUrl("/m/" + merchantSlug + "/admin/subscription?peach=return");
    String notificationUrl = trimSlash(publicBaseUrl) + "/api/public/peach/webhook";

    PeachCheckoutSession session =
        createHostedCheckout(
            amount,
            sub.peachMerchantTransactionId,
            shopperResultUrl,
            notificationUrl,
            sub.peachPaymentMethod);
    sub.peachCheckoutId = session.checkoutId();
    merchantSubscriptions.save(sub);
    return session;
  }

  @Transactional
  public void handleWebhook(Map<String, String> params) {
    if (!peachProperties.isConfigured()) {
      throw new IllegalArgumentException("peach_not_configured");
    }
    if (params == null || params.isEmpty()) {
      throw new IllegalArgumentException("Empty Peach notification");
    }

    String secretToken = peachProperties.getSecretToken();
    String receivedSignature = params.get("signature");
    if (secretToken.isBlank() || receivedSignature == null || receivedSignature.isBlank()) {
      throw new IllegalArgumentException("Missing Peach signature");
    }
    String expected = PeachSignatureUtil.buildSignature(params, secretToken);
    if (!PeachSignatureUtil.signaturesMatch(expected, receivedSignature)) {
      throw new IllegalArgumentException("Invalid Peach signature");
    }

    if (!isSuccessfulNotification(params)) {
      return;
    }

    Optional<OrderEntity> orderOpt = findOrderForNotification(params);
    if (orderOpt.isPresent()) {
      OrderEntity o = orderOpt.get();
      assertNotificationPayload(params, o.totalZar, o.peachMerchantTransactionId);
      String checkoutId = params.get("checkoutId");
      if (checkoutId != null && !checkoutId.isBlank()) {
        o.peachCheckoutId = checkoutId.trim();
        orders.save(o);
      }
      checkoutService.finalizePeachPaidOrder(o);
      return;
    }

    Optional<SalonBookingEntity> bookingOpt = findBookingForNotification(params);
    if (bookingOpt.isPresent()) {
      SalonBookingEntity b = bookingOpt.get();
      SalonServiceEntity svc =
          salonServices
              .findByIdAndTenantId(b.serviceId, b.tenantId)
              .orElseThrow(() -> new IllegalArgumentException("service_not_found"));
      assertNotificationPayload(params, svc.priceZar, b.peachMerchantTransactionId);
      String checkoutId = params.get("checkoutId");
      if (checkoutId != null && !checkoutId.isBlank()) {
        b.peachCheckoutId = checkoutId.trim();
        bookings.save(b);
      }
      salonBookingService.finalizePeachPaidBooking(b);
      return;
    }

    Optional<MerchantSubscriptionEntity> subOpt = findSubscriptionForNotification(params);
    if (subOpt.isPresent()) {
      MerchantSubscriptionEntity sub = subOpt.get();
      SubscriptionPlanPricingEntity pricing =
          sub.planTier != null ? subscriptionPlans.findByTier(sub.planTier).orElse(null) : null;
      if (pricing == null) {
        throw new IllegalArgumentException("plan_missing");
      }
      assertNotificationPayload(
          params, BigDecimal.valueOf(pricing.subscriptionFee), sub.peachMerchantTransactionId);
      String checkoutId = params.get("checkoutId");
      if (checkoutId != null && !checkoutId.isBlank()) {
        sub.peachCheckoutId = checkoutId.trim();
        merchantSubscriptions.save(sub);
      }
      merchantSubscriptionService.finalizePeachPaidSubscription(sub.tenantId);
      return;
    }

    throw new IllegalArgumentException("Payment not found");
  }

  @Transactional(readOnly = true)
  public Map<String, Object> orderStatus(UUID tenantId, UUID orderId, String customerEmail) {
    OrderEntity o = orders.findOneByTenantAndId(tenantId, orderId);
    if (o == null) throw new IllegalArgumentException("not_found");
    String email = customerEmail == null ? "" : customerEmail.trim().toLowerCase(Locale.ROOT);
    if (!email.equalsIgnoreCase(o.customerEmail == null ? "" : o.customerEmail.trim())) {
      throw new IllegalArgumentException("email_mismatch");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", "order");
    out.put("id", o.id.toString());
    out.put("status", o.status.name());
    out.put("paymentMethod", o.paymentMethod == null ? "" : o.paymentMethod.name());
    out.put("peachPaymentMethod", o.peachPaymentMethod == null ? "" : o.peachPaymentMethod.name());
    out.put("paid", o.status == OrderEntity.OrderStatus.paid);
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bookingStatus(UUID tenantId, UUID bookingId, String customerEmail) {
    SalonBookingEntity b = bookings.findOneByTenantAndId(tenantId, bookingId);
    if (b == null) throw new IllegalArgumentException("not_found");
    String email = customerEmail == null ? "" : customerEmail.trim().toLowerCase(Locale.ROOT);
    if (!email.equalsIgnoreCase(b.customerEmail == null ? "" : b.customerEmail.trim())) {
      throw new IllegalArgumentException("email_mismatch");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", "booking");
    out.put("id", b.id.toString());
    out.put("status", b.status.name());
    out.put("paymentMethod", b.clientPaymentMethod == null ? "" : b.clientPaymentMethod.name());
    out.put("peachPaymentMethod", b.peachPaymentMethod == null ? "" : b.peachPaymentMethod.name());
    out.put("paid", b.status == SalonBookingEntity.Status.confirmed);
    return out;
  }

  private void requireConfigured() {
    if (!peachProperties.isConfigured()) {
      throw new IllegalArgumentException("peach_not_configured");
    }
  }

  private void requireShopAcceptsPeach(UUID tenantId) {
    ShopSettingsEntity s = shopSettings.findByTenantId(tenantId).orElse(null);
    boolean allow = s == null || s.acceptCustomerPeach == null || Boolean.TRUE.equals(s.acceptCustomerPeach);
    if (!allow) throw new IllegalArgumentException("peach_not_accepted");
  }

  private PeachCheckoutSession createHostedCheckout(
      BigDecimal amount,
      String merchantTransactionId,
      String shopperResultUrl,
      String notificationUrl,
      PeachPaymentMethod peachPaymentMethod) {
    String amountStr = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("authentication.entityId", peachProperties.getEntityId());
    fields.put("merchantTransactionId", merchantTransactionId);
    fields.put("amount", amountStr);
    fields.put("currency", "ZAR");
    fields.put("paymentType", "DB");
    addPaymentMethodFields(fields, peachPaymentMethod);
    fields.put("nonce", UUID.randomUUID().toString().replace("-", ""));
    fields.put("shopperResultUrl", shopperResultUrl);
    if (notificationUrl != null && !notificationUrl.isBlank()) {
      fields.put("notificationUrl", notificationUrl);
    }

    JsonNode checkoutResponse = createCheckout(fields);
    String checkoutId = textOrNull(checkoutResponse, "checkoutId");
    String redirectUrl = textOrNull(checkoutResponse, "redirectUrl");
    if (checkoutId == null || redirectUrl == null) {
      throw new IllegalArgumentException("Peach checkout did not return a checkoutId/redirectUrl");
    }
    return new PeachCheckoutSession(checkoutId, redirectUrl);
  }

  static void addPaymentMethodFields(
      Map<String, Object> fields, PeachPaymentMethod peachPaymentMethod) {
    // Null only supports Peach rows created before subtype persistence was introduced.
    PeachPaymentMethod selected =
        peachPaymentMethod == null ? PeachPaymentMethod.CARD : peachPaymentMethod;
    fields.put("defaultPaymentMethod", selected.hostedCheckoutMethod());
    fields.put("forceDefaultMethod", true);
  }

  private Optional<OrderEntity> findOrderForNotification(Map<String, String> params) {
    String merchantTransactionId = params.get("merchantTransactionId");
    if (merchantTransactionId != null && !merchantTransactionId.isBlank()) {
      OrderEntity byRef = orders.findFirstByPeachMerchantTransactionId(merchantTransactionId.trim());
      if (byRef != null) return Optional.of(byRef);
    }
    String checkoutId = params.get("checkoutId");
    if (checkoutId != null && !checkoutId.isBlank()) {
      OrderEntity byCheckout = orders.findFirstByPeachCheckoutId(checkoutId.trim());
      if (byCheckout != null) return Optional.of(byCheckout);
    }
    return Optional.empty();
  }

  private Optional<SalonBookingEntity> findBookingForNotification(Map<String, String> params) {
    String merchantTransactionId = params.get("merchantTransactionId");
    if (merchantTransactionId != null && !merchantTransactionId.isBlank()) {
      SalonBookingEntity byRef = bookings.findFirstByPeachMerchantTransactionId(merchantTransactionId.trim());
      if (byRef != null) return Optional.of(byRef);
    }
    String checkoutId = params.get("checkoutId");
    if (checkoutId != null && !checkoutId.isBlank()) {
      SalonBookingEntity byCheckout = bookings.findFirstByPeachCheckoutId(checkoutId.trim());
      if (byCheckout != null) return Optional.of(byCheckout);
    }
    return Optional.empty();
  }

  private Optional<MerchantSubscriptionEntity> findSubscriptionForNotification(Map<String, String> params) {
    String merchantTransactionId = params.get("merchantTransactionId");
    if (merchantTransactionId != null && !merchantTransactionId.isBlank()) {
      MerchantSubscriptionEntity byRef =
          merchantSubscriptions.findFirstByPeachMerchantTransactionId(merchantTransactionId.trim());
      if (byRef != null) return Optional.of(byRef);
    }
    String checkoutId = params.get("checkoutId");
    if (checkoutId != null && !checkoutId.isBlank()) {
      MerchantSubscriptionEntity byCheckout = merchantSubscriptions.findFirstByPeachCheckoutId(checkoutId.trim());
      if (byCheckout != null) return Optional.of(byCheckout);
    }
    return Optional.empty();
  }

  /**
   * Validates currency, amount, and merchantTransactionId on successful Peach notifications.
   * Package-visible for unit tests.
   */
  static void assertNotificationPayload(
      Map<String, String> params, BigDecimal expectedAmount, String expectedMerchantTransactionId) {
    assertCurrencyZar(params);
    assertAmountMatches(params, expectedAmount);
    assertReferenceMatches(params, expectedMerchantTransactionId);
  }

  static void assertCurrencyZar(Map<String, String> params) {
    String currency = params.get("currency");
    if (currency == null || currency.isBlank()) {
      throw new IllegalArgumentException("peach_currency_missing");
    }
    if (!"ZAR".equalsIgnoreCase(currency.trim())) {
      throw new IllegalArgumentException("peach_currency_mismatch");
    }
  }

  static void assertAmountMatches(Map<String, String> params, BigDecimal expected) {
    if (expected == null) {
      throw new IllegalArgumentException("peach_amount_expected_missing");
    }
    String amount = params.get("amount");
    if (amount == null || amount.isBlank()) {
      throw new IllegalArgumentException("peach_amount_missing");
    }
    try {
      BigDecimal paid = new BigDecimal(amount.trim()).setScale(2, RoundingMode.HALF_UP);
      BigDecimal want = expected.setScale(2, RoundingMode.HALF_UP);
      if (paid.subtract(want).abs().compareTo(new BigDecimal("0.02")) > 0) {
        throw new IllegalArgumentException("peach_amount_mismatch");
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid amount from Peach");
    }
  }

  static void assertReferenceMatches(Map<String, String> params, String expectedMerchantTransactionId) {
    if (expectedMerchantTransactionId == null || expectedMerchantTransactionId.isBlank()) {
      return;
    }
    String notified = params.get("merchantTransactionId");
    if (notified == null || notified.isBlank()) {
      throw new IllegalArgumentException("peach_reference_missing");
    }
    if (!expectedMerchantTransactionId.trim().equals(notified.trim())) {
      throw new IllegalArgumentException("peach_reference_mismatch");
    }
  }

  private static String newMerchantTransactionId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  private static boolean isSuccessfulNotification(Map<String, String> params) {
    String status = params.get("status");
    if (status != null && "successful".equalsIgnoreCase(status.trim())) {
      return true;
    }
    String resultCode = params.get("result.code");
    if (resultCode == null || resultCode.isBlank()) {
      return false;
    }
    String code = resultCode.trim();
    return code.startsWith("000.000") || code.startsWith("000.100");
  }

  private JsonNode createCheckout(Map<String, ?> fields) {
    try {
      String accessToken = fetchAccessToken();
      Map<String, Object> body = new LinkedHashMap<>(fields);
      String json = objectMapper.writeValueAsString(body);

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(peachProperties.getCheckoutUrl()))
              .timeout(HTTP_TIMEOUT)
              .header("Content-Type", "application/json")
              .header("Origin", trimSlash(frontendBaseUrl))
              .header("Referer", trimSlash(frontendBaseUrl))
              .header("Authorization", "Bearer " + accessToken)
              .header("accessToken", accessToken)
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();

      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      JsonNode node = objectMapper.readTree(response.body());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        String message = textOrNull(node, "description");
        throw new IllegalArgumentException(
            "Peach checkout request failed" + (message != null ? ": " + message : ""));
      }
      return node;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to reach Peach checkout service", e);
    }
  }

  private synchronized String fetchAccessToken() {
    Instant now = Instant.now();
    if (cachedAccessToken != null && now.isBefore(cachedAccessTokenExpiry)) {
      return cachedAccessToken;
    }
    try {
      Map<String, String> body = new LinkedHashMap<>();
      body.put("clientId", peachProperties.getClientId());
      body.put("clientSecret", peachProperties.getClientSecret());
      body.put("merchantId", peachProperties.getMerchantId());
      String json = objectMapper.writeValueAsString(body);

      HttpRequest httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(peachProperties.getAuthUrl()))
              .timeout(HTTP_TIMEOUT)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();

      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      JsonNode node = objectMapper.readTree(response.body());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalArgumentException("Unable to authenticate with Peach Payments");
      }
      String accessToken = textOrNull(node, "access_token");
      if (accessToken == null || accessToken.isBlank()) {
        throw new IllegalArgumentException("Peach authentication response missing access_token");
      }
      long expiresInSeconds = node.hasNonNull("expires_in") ? node.get("expires_in").asLong(3600) : 3600;
      long safeSeconds = Math.max(expiresInSeconds - TOKEN_EXPIRY_SAFETY_MARGIN_SECONDS, 5);

      cachedAccessToken = accessToken;
      cachedAccessTokenExpiry = now.plusSeconds(safeSeconds);
      return accessToken;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to reach Peach authentication service", e);
    }
  }

  private static String textOrNull(JsonNode node, String field) {
    if (node == null || !node.hasNonNull(field)) {
      return null;
    }
    return node.get(field).asText();
  }

  private static String trimSlash(String url) {
    if (url == null) return "";
    String u = url.trim();
    while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
    return u;
  }

  private String shopperResultUrl(String returnPath) {
    return trimSlash(publicBaseUrl)
        + "/api/public/peach/return?returnPath="
        + urlEncode(returnPath);
  }

  private static String urlEncode(String value) {
    if (value == null) return "";
    try {
      return java.net.URLEncoder.encode(value.trim(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (Exception e) {
      return value.trim();
    }
  }
}
