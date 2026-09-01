package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.config.PayFastProperties;
import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.PeachPaymentMethod;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.SubscriptionPeachPaymentEntity;
import com.productstore.platform.entities.SubscriptionPlanPricingEntity;
import com.productstore.platform.models.PayFastCheckoutResponse;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.SubscriptionPeachPaymentRepository;
import com.productstore.platform.repositories.SubscriptionPlanPricingRepository;
import com.productstore.platform.util.InAppPaymentMethods;
import com.productstore.platform.util.PayFastSignatureUtil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PayFast hosted checkout (form POST) and ITN settlement for the platform merchant account:
 * same mechanism as Wheel Hub, mapped to store orders, salon bookings, and merchant subscriptions.
 */
@Service
public class PayFastPaymentService {

  public static final String CHANNEL_CARD = "CARD";
  public static final String CHANNEL_EFT = "EFT";

  static final String PAYFAST_METHOD_CARD = "cc";
  static final String PAYFAST_METHOD_EFT = "eft";

  static final String CUSTOM_ORDER = "ORDER";
  static final String CUSTOM_BOOKING = "BOOKING";
  static final String CUSTOM_SUB = "SUB";

  private final PayFastProperties payFastProperties;
  private final PlatformFeatureService platformFeatureService;
  private final OrderRepository orders;
  private final SalonBookingRepository bookings;
  private final SalonServiceRepository salonServices;
  private final ShopSettingsRepository shopSettings;
  private final MerchantSubscriptionRepository merchantSubscriptions;
  private final SubscriptionPlanPricingRepository subscriptionPlans;
  private final SubscriptionPeachPaymentRepository subscriptionPeachPayments;
  private final CheckoutService checkoutService;
  private final SalonBookingService salonBookingService;
  private final MerchantSubscriptionService merchantSubscriptionService;
  private final String frontendBaseUrl;

  public PayFastPaymentService(
      PayFastProperties payFastProperties,
      PlatformFeatureService platformFeatureService,
      OrderRepository orders,
      SalonBookingRepository bookings,
      SalonServiceRepository salonServices,
      ShopSettingsRepository shopSettings,
      MerchantSubscriptionRepository merchantSubscriptions,
      SubscriptionPlanPricingRepository subscriptionPlans,
      SubscriptionPeachPaymentRepository subscriptionPeachPayments,
      @Lazy CheckoutService checkoutService,
      @Lazy SalonBookingService salonBookingService,
      @Lazy MerchantSubscriptionService merchantSubscriptionService,
      @Value("${app.frontend-base-url:http://localhost:8085}") String frontendBaseUrl) {
    this.payFastProperties = payFastProperties;
    this.platformFeatureService = platformFeatureService;
    this.orders = orders;
    this.bookings = bookings;
    this.salonServices = salonServices;
    this.shopSettings = shopSettings;
    this.merchantSubscriptions = merchantSubscriptions;
    this.subscriptionPlans = subscriptionPlans;
    this.subscriptionPeachPayments = subscriptionPeachPayments;
    this.checkoutService = checkoutService;
    this.salonBookingService = salonBookingService;
    this.merchantSubscriptionService = merchantSubscriptionService;
    this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
  }

  public boolean isPlatformConfigured() {
    return payFastProperties.isConfigured()
        && platformFeatureService.isEnabled(PlatformFeatureService.PAYMENTS)
        && platformFeatureService.isEnabled(PlatformFeatureService.PAYFAST);
  }

  @Transactional
  public PayFastCheckoutResponse initiateOrderCheckout(UUID tenantId, UUID orderId, String merchantSlug) {
    requirePlatformConfigured();
    requireShopAcceptsInApp(tenantId);
    OrderEntity o = orders.findOneByTenantAndId(tenantId, orderId);
    if (o == null) throw new IllegalArgumentException("not_found");
    if (!InAppPaymentMethods.isInApp(o.paymentMethod)) {
      throw new IllegalArgumentException("not_payfast_order");
    }
    if (o.status != OrderEntity.OrderStatus.pending_payment) {
      throw new IllegalArgumentException("order_not_pending");
    }
    if (o.cancelledAt != null) throw new IllegalArgumentException("order_cancelled");

    if (o.peachMerchantTransactionId == null || o.peachMerchantTransactionId.isBlank()) {
      o.peachMerchantTransactionId = newMerchantPaymentId();
    }
    o.paymentMethod = OrderEntity.PaymentMethod.payfast;
    orders.save(o);

    BigDecimal amount = o.totalZar == null ? BigDecimal.ZERO : o.totalZar.setScale(2, RoundingMode.HALF_UP);
    String returnPath =
        "/m/"
            + merchantSlug
            + "/payfast/return?kind=order&id="
            + o.id
            + "&email="
            + urlEncode(o.customerEmail);
    Map<String, String> fields =
        signedCheckoutFields(
            o.peachMerchantTransactionId,
            amount,
            "Product Store order",
            CUSTOM_ORDER,
            o.id.toString(),
            frontendUrl(returnPath),
            frontendUrl(returnPath + "&cancelled=1"),
            payFastProperties.getPublicBaseUrl() + "/api/payments/payfast/webhook",
            resolvePayFastPaymentMethod(channelFrom(o.peachPaymentMethod)));
    return new PayFastCheckoutResponse(o.id.toString(), payFastProperties.getProcessUrl(), fields);
  }

  @Transactional
  public PayFastCheckoutResponse initiateBookingCheckout(
      UUID tenantId, UUID bookingId, String merchantSlug) {
    requirePlatformConfigured();
    requireShopAcceptsInApp(tenantId);
    SalonBookingEntity b = bookings.findOneByTenantAndId(tenantId, bookingId);
    if (b == null) throw new IllegalArgumentException("not_found");
    if (!InAppPaymentMethods.isInApp(b.clientPaymentMethod)) {
      throw new IllegalArgumentException("not_payfast_booking");
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
    if (b.discountZar != null && b.discountZar.compareTo(BigDecimal.ZERO) > 0) {
      amount = amount.subtract(b.discountZar).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("invalid_amount");
    }

    if (b.peachMerchantTransactionId == null || b.peachMerchantTransactionId.isBlank()) {
      b.peachMerchantTransactionId = newMerchantPaymentId();
    }
    b.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.payfast;
    bookings.save(b);

    String returnPath =
        "/m/"
            + merchantSlug
            + "/payfast/return?kind=booking&id="
            + b.id
            + "&email="
            + urlEncode(b.customerEmail);
    Map<String, String> fields =
        signedCheckoutFields(
            b.peachMerchantTransactionId,
            amount,
            "Product Store booking",
            CUSTOM_BOOKING,
            b.id.toString(),
            frontendUrl(returnPath),
            frontendUrl(returnPath + "&cancelled=1"),
            payFastProperties.getPublicBaseUrl() + "/api/payments/payfast/webhook",
            resolvePayFastPaymentMethod(channelFrom(b.peachPaymentMethod)));
    return new PayFastCheckoutResponse(b.id.toString(), payFastProperties.getProcessUrl(), fields);
  }

  @Transactional
  public PayFastCheckoutResponse initiateSubscriptionCheckout(
      UUID tenantId, String merchantSlug, PeachPaymentMethod peachPaymentMethod) {
    requirePlatformConfigured();
    if (peachPaymentMethod == null) {
      throw new IllegalArgumentException("peach_payment_method_required");
    }
    MerchantSubscriptionEntity sub =
        merchantSubscriptions
            .findByTenantId(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("subscription_not_found"));
    if (merchantSubscriptionService.isBlockingTrialForCheckout(tenantId)) {
      throw new IllegalArgumentException("trial_still_active");
    }
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

    String merchantTransactionId = newSubscriptionMerchantPaymentId();
    SubscriptionPeachPaymentEntity payment = new SubscriptionPeachPaymentEntity();
    payment.tenantId = tenantId;
    payment.planTier = sub.planTier;
    payment.amount = amount;
    payment.currency = "ZAR";
    payment.status = SubscriptionPeachPaymentEntity.STATUS_PENDING;
    payment.peachPaymentMethod = peachPaymentMethod;
    payment.peachMerchantTransactionId = merchantTransactionId;
    subscriptionPeachPayments.save(payment);

    sub.peachMerchantTransactionId = merchantTransactionId;
    sub.peachPaymentMethod = peachPaymentMethod;
    merchantSubscriptions.save(sub);

    String returnPath =
        "/payfast/return?kind=subscription&paymentId="
            + payment.id
            + "&merchant="
            + urlEncode(merchantSlug);
    Map<String, String> fields =
        signedCheckoutFields(
            merchantTransactionId,
            amount,
            "Product Store " + sub.planTier.name() + " subscription",
            CUSTOM_SUB,
            payment.id.toString(),
            frontendUrl(returnPath),
            frontendUrl(returnPath + "&cancelled=1"),
            payFastProperties.getPublicBaseUrl() + "/api/payments/payfast/subscription/webhook",
            resolvePayFastPaymentMethod(channelFrom(peachPaymentMethod)));
    return new PayFastCheckoutResponse(payment.id.toString(), payFastProperties.getProcessUrl(), fields);
  }

  @Transactional
  public void handleWebhook(Map<String, String> params) {
    verifySignature(params);
    if (!isComplete(params)) {
      return;
    }
    String custom = firstNonBlank(params.get("custom_str1"), params.get("customStr1"));
    if (custom != null && CUSTOM_SUB.equalsIgnoreCase(custom.trim())) {
      throw new IllegalArgumentException("Unexpected PayFast notification type");
    }
    if (custom != null && CUSTOM_BOOKING.equalsIgnoreCase(custom.trim())) {
      settleBooking(params);
      return;
    }
    settleOrder(params);
  }

  @Transactional
  public void handleSubscriptionWebhook(Map<String, String> params) {
    verifySignature(params);
    if (!isComplete(params)) {
      return;
    }
    SubscriptionPeachPaymentEntity payment =
        findSubscriptionPaymentForNotification(params)
            .orElseThrow(() -> new IllegalArgumentException("Subscription payment not found"));
    validateSubscriptionNotification(params, payment);
    if (SubscriptionPeachPaymentEntity.STATUS_COMPLETED.equalsIgnoreCase(payment.status)) {
      return;
    }
    if (!SubscriptionPeachPaymentEntity.STATUS_PENDING.equalsIgnoreCase(payment.status)) {
      throw new IllegalArgumentException("Subscription payment is not awaiting PayFast settlement");
    }
    String pfPaymentId = firstNonBlank(params.get("pf_payment_id"), params.get("pfPaymentId"));
    if (pfPaymentId != null && !pfPaymentId.isBlank()) {
      payment.peachCheckoutId = pfPaymentId.trim();
    }
    payment.status = SubscriptionPeachPaymentEntity.STATUS_COMPLETED;
    payment.completedAt = Instant.now();
    subscriptionPeachPayments.save(payment);

    MerchantSubscriptionEntity sub = merchantSubscriptions.findByTenantId(payment.tenantId).orElse(null);
    if (sub != null) {
      if (payment.peachCheckoutId != null) {
        sub.peachCheckoutId = payment.peachCheckoutId;
      }
      sub.peachMerchantTransactionId = payment.peachMerchantTransactionId;
      sub.peachPaymentMethod = payment.peachPaymentMethod;
      merchantSubscriptions.save(sub);
    }
    merchantSubscriptionService.finalizePeachPaidSubscription(payment.tenantId, payment.planTier);
  }

  static String resolvePayFastPaymentMethod(String channel) {
    String normalized = normalizeChannel(channel);
    return CHANNEL_EFT.equals(normalized) ? PAYFAST_METHOD_EFT : PAYFAST_METHOD_CARD;
  }

  static Map<String, String> buildUnsignedCheckoutFields(
      String merchantId,
      String merchantKey,
      String returnUrl,
      String cancelUrl,
      String notifyUrl,
      String merchantPaymentId,
      String amount,
      String itemName,
      String customStr1,
      String customStr2,
      String paymentMethod) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("merchant_id", merchantId);
    fields.put("merchant_key", merchantKey);
    fields.put("return_url", returnUrl);
    fields.put("cancel_url", cancelUrl);
    fields.put("notify_url", notifyUrl);
    fields.put("m_payment_id", merchantPaymentId);
    fields.put("amount", amount);
    fields.put("item_name", itemName);
    fields.put("custom_str1", customStr1);
    fields.put("custom_str2", customStr2);
    fields.put("payment_method", paymentMethod);
    return fields;
  }

  private void settleOrder(Map<String, String> params) {
    OrderEntity o =
        findOrderForNotification(params).orElseThrow(() -> new IllegalArgumentException("Payment not found"));
    validateAmount(params, o.totalZar, "PayFast amount does not match order total");
    String merchantRef = firstNonBlank(params.get("m_payment_id"), params.get("mPaymentId"));
    if (merchantRef == null
        || o.peachMerchantTransactionId == null
        || !o.peachMerchantTransactionId.equals(merchantRef.trim())) {
      throw new IllegalArgumentException("PayFast merchant reference does not match");
    }
    String custom = firstNonBlank(params.get("custom_str1"), params.get("customStr1"));
    if (custom != null && !custom.isBlank() && !CUSTOM_ORDER.equalsIgnoreCase(custom.trim())) {
      throw new IllegalArgumentException("Unexpected PayFast notification type");
    }
    String pfPaymentId = firstNonBlank(params.get("pf_payment_id"), params.get("pfPaymentId"));
    if (pfPaymentId != null && !pfPaymentId.isBlank()) {
      o.peachCheckoutId = pfPaymentId.trim();
      orders.save(o);
    }
    checkoutService.finalizePeachPaidOrder(o);
  }

  private void settleBooking(Map<String, String> params) {
    SalonBookingEntity b =
        findBookingForNotification(params)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
    SalonServiceEntity svc =
        salonServices
            .findByIdAndTenantId(b.serviceId, b.tenantId)
            .orElseThrow(() -> new IllegalArgumentException("service_not_found"));
    BigDecimal payable =
        svc.priceZar == null ? BigDecimal.ZERO : svc.priceZar.setScale(2, RoundingMode.HALF_UP);
    if (b.discountZar != null && b.discountZar.compareTo(BigDecimal.ZERO) > 0) {
      payable = payable.subtract(b.discountZar).max(BigDecimal.ZERO);
    }
    validateAmount(params, payable, "PayFast amount does not match booking total");
    String merchantRef = firstNonBlank(params.get("m_payment_id"), params.get("mPaymentId"));
    if (merchantRef == null
        || b.peachMerchantTransactionId == null
        || !b.peachMerchantTransactionId.equals(merchantRef.trim())) {
      throw new IllegalArgumentException("PayFast merchant reference does not match");
    }
    String pfPaymentId = firstNonBlank(params.get("pf_payment_id"), params.get("pfPaymentId"));
    if (pfPaymentId != null && !pfPaymentId.isBlank()) {
      b.peachCheckoutId = pfPaymentId.trim();
      bookings.save(b);
    }
    salonBookingService.finalizePeachPaidBooking(b);
  }

  private Optional<OrderEntity> findOrderForNotification(Map<String, String> params) {
    String merchantRef = firstNonBlank(params.get("m_payment_id"), params.get("mPaymentId"));
    if (merchantRef != null && !merchantRef.isBlank()) {
      OrderEntity byRef = orders.findFirstByPeachMerchantTransactionId(merchantRef.trim());
      if (byRef != null) {
        return Optional.of(byRef);
      }
    }
    String paymentId = firstNonBlank(params.get("custom_str2"), params.get("customStr2"));
    if (paymentId != null && !paymentId.isBlank()) {
      try {
        return orders.findById(UUID.fromString(paymentId.trim()));
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private Optional<SalonBookingEntity> findBookingForNotification(Map<String, String> params) {
    String merchantRef = firstNonBlank(params.get("m_payment_id"), params.get("mPaymentId"));
    if (merchantRef != null && !merchantRef.isBlank()) {
      SalonBookingEntity byRef = bookings.findFirstByPeachMerchantTransactionId(merchantRef.trim());
      if (byRef != null) {
        return Optional.of(byRef);
      }
    }
    String paymentId = firstNonBlank(params.get("custom_str2"), params.get("customStr2"));
    if (paymentId != null && !paymentId.isBlank()) {
      try {
        return bookings.findById(UUID.fromString(paymentId.trim()));
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private Optional<SubscriptionPeachPaymentEntity> findSubscriptionPaymentForNotification(
      Map<String, String> params) {
    String merchantRef = firstNonBlank(params.get("m_payment_id"), params.get("mPaymentId"));
    if (merchantRef != null && !merchantRef.isBlank()) {
      Optional<SubscriptionPeachPaymentEntity> byRef =
          subscriptionPeachPayments.findByPeachMerchantTransactionIdForUpdate(merchantRef.trim());
      if (byRef.isPresent()) {
        return byRef;
      }
    }
    String paymentId = firstNonBlank(params.get("custom_str2"), params.get("customStr2"));
    if (paymentId != null && !paymentId.isBlank()) {
      try {
        return subscriptionPeachPayments.findById(UUID.fromString(paymentId.trim()));
      } catch (IllegalArgumentException e) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private static void validateSubscriptionNotification(
      Map<String, String> params, SubscriptionPeachPaymentEntity payment) {
    String merchantRef = firstNonBlank(params.get("m_payment_id"), params.get("mPaymentId"));
    if (merchantRef == null
        || merchantRef.isBlank()
        || payment.peachMerchantTransactionId == null
        || !payment.peachMerchantTransactionId.equals(merchantRef.trim())) {
      throw new IllegalArgumentException("PayFast merchant reference does not match");
    }
    String custom = firstNonBlank(params.get("custom_str1"), params.get("customStr1"));
    if (custom != null && !custom.isBlank() && !CUSTOM_SUB.equalsIgnoreCase(custom.trim())) {
      throw new IllegalArgumentException("Unexpected PayFast notification type");
    }
    validateAmount(params, payment.amount, "PayFast amount does not match");
  }

  private static void validateAmount(Map<String, String> params, BigDecimal expected, String mismatchMessage) {
    String amount = firstNonBlank(params.get("amount_gross"), params.get("amountGross"), params.get("amount"));
    if (amount == null || amount.isBlank()) {
      throw new IllegalArgumentException("Missing amount from PayFast");
    }
    try {
      BigDecimal paid = new BigDecimal(amount.trim());
      BigDecimal want = expected == null ? BigDecimal.ZERO : expected;
      if (paid.subtract(want).abs().compareTo(new BigDecimal("0.02")) > 0) {
        throw new IllegalArgumentException(mismatchMessage);
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid amount from PayFast");
    }
  }

  private Map<String, String> signedCheckoutFields(
      String merchantPaymentId,
      BigDecimal amount,
      String itemName,
      String customStr1,
      String customStr2,
      String returnUrl,
      String cancelUrl,
      String notifyUrl,
      String paymentMethod) {
    String amountStr = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    Map<String, String> fields =
        buildUnsignedCheckoutFields(
            payFastProperties.getMerchantId(),
            payFastProperties.getMerchantKey(),
            returnUrl,
            cancelUrl,
            notifyUrl,
            merchantPaymentId,
            amountStr,
            itemName,
            customStr1,
            customStr2,
            paymentMethod);
    fields.put("signature", PayFastSignatureUtil.buildSignature(fields, payFastProperties.getPassphrase()));
    return fields;
  }

  private void requirePlatformConfigured() {
    if (!payFastProperties.isConfigured()) {
      throw new IllegalArgumentException("PayFast is unavailable: " + payFastProperties.configurationStatus());
    }
    if (!platformFeatureService.isEnabled(PlatformFeatureService.PAYMENTS)
        || !platformFeatureService.isEnabled(PlatformFeatureService.PAYFAST)) {
      throw new IllegalArgumentException("PayFast is currently disabled by the platform.");
    }
  }

  private void requireShopAcceptsInApp(UUID tenantId) {
    ShopSettingsEntity s = shopSettings.findByTenantId(tenantId).orElse(null);
    boolean allow = s == null || s.acceptCustomerPeach == null || Boolean.TRUE.equals(s.acceptCustomerPeach);
    if (!allow) {
      throw new IllegalArgumentException("peach_not_accepted");
    }
  }

  private void verifySignature(Map<String, String> params) {
    if (params == null || params.isEmpty()) {
      throw new IllegalArgumentException("Empty PayFast notification");
    }
    String passphrase = payFastProperties.getPassphrase();
    String receivedSignature = params.get("signature");
    if (passphrase == null
        || passphrase.isBlank()
        || receivedSignature == null
        || receivedSignature.isBlank()) {
      throw new IllegalArgumentException("Missing PayFast signature");
    }
    String expected = PayFastSignatureUtil.buildSignature(params, passphrase);
    if (!PayFastSignatureUtil.signaturesMatch(expected, receivedSignature)) {
      throw new IllegalArgumentException("Invalid PayFast signature");
    }
  }

  private static boolean isComplete(Map<String, String> params) {
    String status = firstNonBlank(params.get("payment_status"), params.get("paymentStatus"));
    return status != null && "COMPLETE".equalsIgnoreCase(status.trim());
  }

  private static String channelFrom(PeachPaymentMethod method) {
    if (method == PeachPaymentMethod.EFT) return CHANNEL_EFT;
    return CHANNEL_CARD;
  }

  static String normalizeChannel(String payFastPaymentMethod) {
    if (payFastPaymentMethod == null || payFastPaymentMethod.isBlank()) {
      return CHANNEL_CARD;
    }
    String u = payFastPaymentMethod.trim().toUpperCase(Locale.ROOT);
    if (CHANNEL_CARD.equals(u) || CHANNEL_EFT.equals(u)) {
      return u;
    }
    throw new IllegalArgumentException("payFastPaymentMethod must be CARD or EFT");
  }

  private static String newMerchantPaymentId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  }

  private static String newSubscriptionMerchantPaymentId() {
    return ("SUB" + UUID.randomUUID().toString().replace("-", "")).substring(0, 16);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private String frontendUrl(String path) {
    String base = frontendBaseUrl;
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    if (path == null || path.isBlank()) {
      return base;
    }
    return path.startsWith("/") ? base + path : base + "/" + path;
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }
}
