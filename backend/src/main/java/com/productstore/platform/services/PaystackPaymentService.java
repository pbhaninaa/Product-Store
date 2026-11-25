package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.config.CustomerPaymentPolicy;
import com.productstore.platform.config.PaystackProperties;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.OnlinePaymentAttemptEntity;
import com.productstore.platform.entities.MerchantSubscriptionEntity;
import com.productstore.platform.entities.OnlinePaymentChannel;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.SubscriptionOnlinePaymentEntity;
import com.productstore.platform.entities.SubscriptionPlanPricingEntity;
import com.productstore.platform.models.OnlineCheckoutSession;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.SubscriptionOnlinePaymentRepository;
import com.productstore.platform.repositories.SubscriptionPlanPricingRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.OnlinePaymentSettlementService.ConfirmedPayment;
import com.productstore.platform.services.OnlinePaymentSettlementService.Outcome;
import com.productstore.platform.services.auth.Role;
import com.productstore.platform.util.Emails;
import com.productstore.platform.util.OnlinePaymentMethods;
import com.productstore.platform.util.RefCodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Paystack hosted checkout for store orders, salon bookings and merchant subscriptions, all paid into the
 * single platform Paystack account. Starts payments ({@code /transaction/initialize}), settles them from
 * signed {@code charge.success} webhooks, and verifies still pending payments when the return page asks for
 * their status or the reconciliation job runs, so payments confirm even when no webhook ever arrives (the
 * Paystack account is shared and may send its webhooks elsewhere).
 *
 * <p>Every checkout attempt gets a new reference (Paystack never accepts a reference twice). Only references
 * this app created ({@code ORD}/{@code BKG}/{@code SUB} + 16 letters or digits) with matching metadata are
 * ever settled, so another app's transactions on the shared account are never touched. Database work runs in
 * short transactions; calls to Paystack run outside them so no connection or row lock is held meanwhile.
 */
@Service
public class PaystackPaymentService {
  static final String CHANNEL_CARD = "card";
  static final String CHANNEL_EFT = "eft";
  static final String EVENT_CHARGE_SUCCESS = "charge.success";
  static final String EVENT_REFUND_PREFIX = "refund.";
  static final int REFERENCE_RANDOM_LENGTH = 16;
  private static final String REFERENCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final Pattern OWN_REFERENCE = Pattern.compile("^(ORD|BKG|SUB)[A-Z0-9]{16}$");
  private static final SecureRandom RANDOM = new SecureRandom();

  private static final Logger log = LoggerFactory.getLogger(PaystackPaymentService.class);

  /** The webhook signature is missing or wrong, or no secret key is configured to check it. */
  public static class InvalidWebhookSignatureException extends RuntimeException {
    public InvalidWebhookSignatureException() {
      super("invalid_signature");
    }
  }

  /** What asking Paystack about one reference achieved. */
  public enum VerifyResult {
    /** Not asked (not pending, not online, no reference or no secret key). */
    NOT_CHECKED,
    /** Paystack confirmed the payment and it was settled now. */
    SETTLED,
    /** Paystack confirmed the payment and the record was already paid. */
    ALREADY_SETTLED,
    /** Paystack reports the checkout as abandoned or failed: the customer did not pay. */
    NOT_PAID,
    /** Still in progress, or Paystack does not know the reference yet. */
    PENDING,
    /** Paystack answered, but the transaction is not this record's payment or does not match it. */
    REJECTED,
    /** Paystack could not be asked. */
    ERROR,
    /** Paid, but the record was already paid by another payment (recorded on the attempt). */
    DUPLICATE,
    /** Paid, but it could not be applied; a person must refund (recorded on the attempt). */
    NEEDS_REFUND,
    /** Paid (a booking fee) after its booking was cancelled: refunded automatically. */
    REFUNDING;

    /** The record is paid (by this payment or, for a duplicate, by another one). */
    public boolean isPaid() {
      return this == SETTLED || this == ALREADY_SETTLED || this == DUPLICATE;
    }
  }

  private final PaystackProperties properties;
  private final PaystackClient paystack;
  private final PaystackVerifier verifier;
  private final OnlinePaymentAvailability availability;
  private final OnlinePaymentSettlementService settlement;
  private final OnlinePaymentAttemptRepository attempts;
  private final OrderRepository orders;
  private final SalonBookingRepository bookings;
  private final SalonServiceRepository salonServices;
  private final ShopSettingsRepository shopSettings;
  private final MerchantSubscriptionRepository merchantSubscriptions;
  private final SubscriptionPlanPricingRepository subscriptionPlans;
  private final SubscriptionOnlinePaymentRepository subscriptionPayments;
  private final MembershipRepository memberships;
  private final UserRepository users;
  private final CheckoutService checkoutService;
  private final SalonBookingService salonBookingService;
  private final MerchantSubscriptionService merchantSubscriptionService;
  private final BookingFeeRefundService bookingFeeRefunds;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;
  private final String frontendBaseUrl;

  public PaystackPaymentService(
      PaystackProperties properties,
      PaystackClient paystack,
      PaystackVerifier verifier,
      OnlinePaymentAvailability availability,
      OnlinePaymentSettlementService settlement,
      OnlinePaymentAttemptRepository attempts,
      OrderRepository orders,
      SalonBookingRepository bookings,
      SalonServiceRepository salonServices,
      ShopSettingsRepository shopSettings,
      MerchantSubscriptionRepository merchantSubscriptions,
      SubscriptionPlanPricingRepository subscriptionPlans,
      SubscriptionOnlinePaymentRepository subscriptionPayments,
      MembershipRepository memberships,
      UserRepository users,
      CheckoutService checkoutService,
      SalonBookingService salonBookingService,
      MerchantSubscriptionService merchantSubscriptionService,
      ObjectMapper mapper,
      PlatformTransactionManager transactionManager,
      @Value("${app.frontend-base-url:http://localhost:8085}") String frontendBaseUrl,
      BookingFeeRefundService bookingFeeRefunds) {
    this.bookingFeeRefunds = bookingFeeRefunds;
    this.properties = properties;
    this.paystack = paystack;
    this.verifier = verifier;
    this.availability = availability;
    this.settlement = settlement;
    this.attempts = attempts;
    this.orders = orders;
    this.bookings = bookings;
    this.salonServices = salonServices;
    this.shopSettings = shopSettings;
    this.merchantSubscriptions = merchantSubscriptions;
    this.subscriptionPlans = subscriptionPlans;
    this.subscriptionPayments = subscriptionPayments;
    this.memberships = memberships;
    this.users = users;
    this.checkoutService = checkoutService;
    this.salonBookingService = salonBookingService;
    this.merchantSubscriptionService = merchantSubscriptionService;
    this.mapper = mapper;
    this.tx = new TransactionTemplate(transactionManager);
    this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.trim();
  }

  /** True when new online payments may start: Paystack configured and not switched off by the platform. */
  public boolean isPlatformConfigured() {
    return availability.isAvailable();
  }

  /** Paystack's smallest charge in rand (e.g. {@code 1.00}); a smaller booking fee is not charged online. */
  public String minimumChargeZar() {
    return BigDecimal.valueOf(availability.minimumChargeCents(), 2).toPlainString();
  }

  /** True for a reference this app created; anything else on the shared Paystack account is ignored. */
  public static boolean isOwnReference(String reference) {
    return reference != null && OWN_REFERENCE.matcher(reference.trim()).matches();
  }

  // ------------------------------------------------------------------ start payment

  private record PreparedCheckout(
      String paymentId,
      String email,
      long amountCents,
      String reference,
      String callbackUrl,
      OnlinePaymentChannel channel,
      String kind) {}

  /**
   * Starts payment for an order that was just placed. If the payment cannot start, the order is cancelled
   * in its own transaction (stock reservation and promotion use given back) and the error is rethrown, so
   * no unpayable order is left holding stock.
   */
  public OnlineCheckoutSession startNewOrderCheckout(UUID tenantId, UUID orderId, String merchantSlug) {
    try {
      return initiateOrderCheckout(tenantId, orderId, merchantSlug);
    } catch (RuntimeException e) {
      boolean undone = checkoutService.cancelUnstartedOnlineOrder(tenantId, orderId);
      log.warn(
          "Online payment could not start for new order {} ({}); order cancelled: {}",
          RefCodes.shortRef(orderId), e.getMessage(), undone);
      throw e;
    }
  }

  /** Same as {@link #startNewOrderCheckout} for a booking: on failure the booking is cancelled and the slot freed. */
  public OnlineCheckoutSession startNewBookingCheckout(UUID tenantId, UUID bookingId, String merchantSlug) {
    try {
      return initiateBookingCheckout(tenantId, bookingId, merchantSlug);
    } catch (RuntimeException e) {
      boolean undone = salonBookingService.cancelUnstartedOnlineBooking(tenantId, bookingId);
      log.warn(
          "Online payment could not start for new booking {} ({}); booking cancelled: {}",
          RefCodes.shortRef(bookingId), e.getMessage(), undone);
      throw e;
    }
  }

  public OnlineCheckoutSession initiateOrderCheckout(UUID tenantId, UUID orderId, String merchantSlug) {
    availability.requireAvailable();
    refuseIfAnEarlierAttemptWasPaid(OnlinePaymentAttemptEntity.Kind.ORDER, tenantId, orderId);
    PreparedCheckout prepared =
        tx.execute(
            status -> {
              OrderEntity o = orders.lockById(orderId).filter(r -> tenantId.equals(r.tenantId)).orElse(null);
              if (o == null) throw new IllegalArgumentException("not_found");
              requireShopAcceptsOnline(tenantId);
              if (!OnlinePaymentMethods.isOnline(o.paymentMethod)) {
                throw new IllegalArgumentException("not_online_order");
              }
              if (o.status != OrderEntity.OrderStatus.pending_payment) {
                throw new IllegalArgumentException("order_not_pending");
              }
              if (o.cancelledAt != null) throw new IllegalArgumentException("order_cancelled");
              BigDecimal amount = o.totalZar == null ? BigDecimal.ZERO : o.totalZar.setScale(2, RoundingMode.HALF_UP);
              if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("invalid_amount");
              o.onlinePaymentReference = newReference(OnlinePaymentSettlementService.PREFIX_ORDER);
              orders.save(o);
              recordAttempt(OnlinePaymentAttemptEntity.Kind.ORDER, o.id, tenantId, o.onlinePaymentReference, amount);
              return new PreparedCheckout(
                  o.id.toString(),
                  o.customerEmail,
                  OnlinePaymentSettlementService.toCents(amount),
                  o.onlinePaymentReference,
                  frontendUrl("/m/" + urlEncode(merchantSlug) + "/payment/return?kind=order&id=" + o.id),
                  o.onlinePaymentChannel,
                  OnlinePaymentSettlementService.KIND_ORDER);
            });
    return startCheckout(prepared);
  }

  public OnlineCheckoutSession initiateBookingCheckout(UUID tenantId, UUID bookingId, String merchantSlug) {
    availability.requireAvailable();
    refuseIfAnEarlierAttemptWasPaid(OnlinePaymentAttemptEntity.Kind.BOOKING, tenantId, bookingId);
    PreparedCheckout prepared =
        tx.execute(
            status -> {
              SalonBookingEntity b =
                  bookings.lockById(bookingId).filter(r -> tenantId.equals(r.tenantId)).orElse(null);
              if (b == null) throw new IllegalArgumentException("not_found");
              BigDecimal amount;
              if (b.hasBookingFee()) {
                // A pay-in-store booking with a booking fee: only the fee is paid online (the shop's own in-app
                // payment toggle is about paying the full price online and does not apply).
                if (BookingFeeService.feePaid(b)) throw new IllegalArgumentException("already_paid");
                if (b.status != SalonBookingEntity.Status.pending
                    || b.bookingFeeStatus != SalonBookingEntity.BookingFeeStatus.awaiting_payment) {
                  throw new IllegalArgumentException("booking_not_pending");
                }
                amount = b.bookingFeeZar.setScale(2, RoundingMode.HALF_UP);
              } else {
                requireShopAcceptsOnline(tenantId);
                if (!OnlinePaymentMethods.isOnline(b.clientPaymentMethod)) {
                  throw new IllegalArgumentException("not_online_booking");
                }
                if (b.status != SalonBookingEntity.Status.pending) {
                  throw new IllegalArgumentException("booking_not_pending");
                }
                amount = bookingPayable(b);
              }
              if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("invalid_amount");
              b.onlinePaymentReference = newReference(OnlinePaymentSettlementService.PREFIX_BOOKING);
              bookings.save(b);
              recordAttempt(OnlinePaymentAttemptEntity.Kind.BOOKING, b.id, tenantId, b.onlinePaymentReference, amount);
              return new PreparedCheckout(
                  b.id.toString(),
                  b.customerEmail,
                  OnlinePaymentSettlementService.toCents(amount),
                  b.onlinePaymentReference,
                  frontendUrl("/m/" + urlEncode(merchantSlug) + "/payment/return?kind=booking&id=" + b.id),
                  b.onlinePaymentChannel,
                  OnlinePaymentSettlementService.KIND_BOOKING);
            });
    return startCheckout(prepared);
  }

  /**
   * Every checkout attempt is kept in the ledger, so a payment of any attempt (not only the latest) is found
   * by the webhook, the return page and the reconciliation job.
   */
  private void recordAttempt(
      OnlinePaymentAttemptEntity.Kind kind, UUID recordId, UUID tenantId, String reference, BigDecimal amount) {
    OnlinePaymentAttemptEntity a = new OnlinePaymentAttemptEntity();
    a.kind = kind;
    a.recordId = recordId;
    a.tenantId = tenantId;
    a.reference = reference;
    a.amountCents = OnlinePaymentSettlementService.toCents(amount);
    a.startedAt = Instant.now();
    a.outcome = OnlinePaymentAttemptEntity.Outcome.PENDING;
    attempts.save(a);
  }

  /**
   * Before a new attempt starts, earlier attempts that are still pending are checked with Paystack (through
   * the verify cache): the customer may have paid one already. A paid one is settled and the new attempt is
   * refused with {@code already_paid}, so the customer is not charged twice.
   */
  private void refuseIfAnEarlierAttemptWasPaid(OnlinePaymentAttemptEntity.Kind kind, UUID tenantId, UUID recordId) {
    for (OnlinePaymentAttemptEntity a :
        attempts.findByKindAndRecordIdAndOutcome(kind, recordId, OnlinePaymentAttemptEntity.Outcome.PENDING)) {
      if (!tenantId.equals(a.tenantId)) continue;
      if (verifyAndSettle(a.reference, a.kindName(), recordId).isPaid()) {
        throw new IllegalArgumentException("already_paid");
      }
    }
  }

  /** Merchant pays the plan fee into the platform account; one ledger row per checkout attempt. */
  public OnlineCheckoutSession initiateSubscriptionCheckout(
      UUID tenantId, String merchantSlug, OnlinePaymentChannel channel) {
    availability.requireAvailable();
    if (channel == null) {
      throw new IllegalArgumentException("online_payment_channel_required");
    }
    PreparedCheckout prepared =
        tx.execute(
            status -> {
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
                  subscriptionPlans
                      .findByTier(sub.planTier)
                      .orElseThrow(() -> new IllegalArgumentException("plan_missing"));
              BigDecimal amount = BigDecimal.valueOf(pricing.subscriptionFee).setScale(2, RoundingMode.HALF_UP);
              if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("invalid_amount");
              }
              String email = ownerEmail(tenantId);

              SubscriptionOnlinePaymentEntity payment = new SubscriptionOnlinePaymentEntity();
              payment.tenantId = tenantId;
              payment.planTier = sub.planTier;
              payment.amount = amount;
              payment.currency = OnlinePaymentSettlementService.CURRENCY;
              payment.status = SubscriptionOnlinePaymentEntity.STATUS_PENDING;
              payment.onlinePaymentChannel = channel;
              payment.onlinePaymentReference = newReference(OnlinePaymentSettlementService.PREFIX_SUBSCRIPTION);
              subscriptionPayments.save(payment);

              sub.onlinePaymentReference = payment.onlinePaymentReference;
              sub.onlinePaymentChannel = channel;
              merchantSubscriptions.save(sub);

              String callback =
                  "/payment/return?kind=subscription&paymentId=" + payment.id + "&merchant="
                      + urlEncode(merchantSlug);
              return new PreparedCheckout(
                  payment.id.toString(),
                  email,
                  OnlinePaymentSettlementService.toCents(amount),
                  payment.onlinePaymentReference,
                  frontendUrl(callback),
                  channel,
                  OnlinePaymentSettlementService.KIND_SUBSCRIPTION);
            });
    return startCheckout(prepared);
  }

  private OnlineCheckoutSession startCheckout(PreparedCheckout p) {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("kind", p.kind());
    metadata.put("id", p.paymentId());
    PaystackClient.InitializeRequest request =
        new PaystackClient.InitializeRequest(
            p.email(),
            p.amountCents(),
            OnlinePaymentSettlementService.CURRENCY,
            p.reference(),
            p.callbackUrl(),
            List.of(channelFor(p.channel())),
            metadata);
    try {
      PaystackClient.InitializeResult result = paystack.initialize(request);
      return new OnlineCheckoutSession(p.paymentId(), result.authorizationUrl());
    } catch (PaystackClient.PaystackException e) {
      log.warn("Paystack checkout could not start for {} reference {}: {}", p.kind(), p.reference(), e.getMessage());
      throw new IllegalArgumentException("online_payment_unavailable");
    }
  }

  /** Paystack {@code channels} value for the customer's choice; null (old rows) means card. */
  static String channelFor(OnlinePaymentChannel channel) {
    return channel == OnlinePaymentChannel.EFT ? CHANNEL_EFT : CHANNEL_CARD;
  }

  /** Our channel for what Paystack reports was used; null when it is neither card nor EFT. */
  static OnlinePaymentChannel channelFrom(String paystackChannel) {
    if (paystackChannel == null) return null;
    return switch (paystackChannel.trim().toLowerCase(Locale.ROOT)) {
      case CHANNEL_CARD -> OnlinePaymentChannel.CARD;
      case CHANNEL_EFT -> OnlinePaymentChannel.EFT;
      default -> null;
    };
  }

  // ------------------------------------------------------------------ webhook

  /**
   * Handles a Paystack webhook. {@code rawBody} must be the exact bytes received: the signature is a hex
   * HMAC SHA512 of them keyed with the secret key. Only {@code charge.success} for a reference this app
   * created settles anything.
   *
   * @throws InvalidWebhookSignatureException when the signature does not verify
   * @throws IllegalArgumentException when a signed payment does not match the record (amount, currency) or,
   *     on PROD, is not a live transaction
   */
  public Outcome handleWebhook(byte[] rawBody, String signature) {
    if (!signatureMatches(rawBody, signature, properties.getSecretKey())) {
      throw new InvalidWebhookSignatureException();
    }
    JsonNode root;
    try {
      root = mapper.readTree(rawBody);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid_webhook_payload");
    }
    String event = root == null ? null : root.path("event").asText(null);
    if (event != null && event.startsWith(EVENT_REFUND_PREFIX)) {
      handleRefundEvent(event, root.path("data"));
      return Outcome.NOT_FOUND;
    }
    if (!EVENT_CHARGE_SUCCESS.equals(event)) {
      return Outcome.NOT_FOUND;
    }
    PaystackClient.Transaction transaction;
    try {
      transaction = PaystackClient.Transaction.fromJson(root.get("data"), mapper);
    } catch (PaystackClient.PaystackException e) {
      throw new IllegalArgumentException("invalid_webhook_payload");
    }
    if (!transaction.isSuccess()) {
      return Outcome.NOT_FOUND;
    }
    if (!isOwnReference(transaction.reference())) {
      // The Paystack account is shared: another app's payment, never ours to touch.
      return Outcome.NOT_FOUND;
    }
    requireLiveOnProd(transaction);
    Outcome outcome = applyConfirmed(toConfirmed(transaction));
    if (outcome == Outcome.NOT_FOUND) {
      log.info("Paystack charge.success with reference {} matches no record; ignored", transaction.reference());
    }
    return outcome;
  }

  /**
   * {@code refund.processed} / {@code refund.failed} for one of our booking fee refunds (found by the refunded
   * transaction's reference). Other refund events, and refunds of another app's transactions on the shared account,
   * are ignored. Idempotent: a replay changes nothing.
   */
  private void handleRefundEvent(String event, JsonNode data) {
    String reference = data == null ? null : data.path("transaction_reference").asText(null);
    if (reference == null && data != null) {
      reference = data.path("transaction").path("reference").asText(null);
    }
    if (!isOwnReference(reference)) {
      return;
    }
    String status = event.substring(EVENT_REFUND_PREFIX.length());
    boolean changed = bookingFeeRefunds.onRefundNotification(reference.trim(), status);
    if (changed) {
      log.info("Paystack {} applied to reference {}", event, reference.trim());
    }
  }

  /** Constant time check of {@code x-paystack-signature}. False when no secret key is configured. */
  static boolean signatureMatches(byte[] rawBody, String signature, String secretKey) {
    if (rawBody == null || signature == null || signature.isBlank() || secretKey == null || secretKey.isBlank()) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA512");
      mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      byte[] expected = HexFormat.of().formatHex(mac.doFinal(rawBody)).getBytes(StandardCharsets.US_ASCII);
      byte[] received = signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
      return MessageDigest.isEqual(expected, received);
    } catch (Exception e) {
      return false;
    }
  }

  /** Defence in depth: PROD only ever settles live Paystack transactions. */
  private void requireLiveOnProd(PaystackClient.Transaction t) {
    if (properties.isProdProfile() && !t.isLive()) {
      log.error("Refused Paystack transaction {} on PROD: domain is not live", t.reference());
      throw new IllegalArgumentException("online_payment_not_live");
    }
  }

  /**
   * Settles a confirmed payment. If marking the record paid fails after the payment was accepted, the
   * settle transaction rolls back and the attempt is recorded as needing a refund in a new transaction,
   * so the money taken is never silently lost.
   */
  private Outcome applyConfirmed(ConfirmedPayment payment) {
    try {
      return settlement.settle(payment);
    } catch (OnlinePaymentSettlementService.AttemptCannotBeAppliedException e) {
      return settlement.recordNeedsRefund(e.attemptId(), e.reference(), e.getMessage());
    }
  }

  private static ConfirmedPayment toConfirmed(PaystackClient.Transaction t) {
    return new ConfirmedPayment(
        t.reference(),
        t.id(),
        t.amountCents(),
        t.chargedCoversRequested(),
        t.currency(),
        channelFrom(t.channel()),
        t.metadata().get("kind"),
        t.metadata().get("id"),
        t.feeBorneCents());
  }

  // ------------------------------------------------------------------ verify + settle

  /**
   * Asks Paystack (through the short verify cache) about {@code reference} and settles the record when
   * Paystack says it succeeded. The answer must be for exactly that reference and, when {@code kind}/{@code
   * recordId} are given, carry our metadata for that record. Never throws: problems are logged and reported
   * as {@link VerifyResult#ERROR} or {@link VerifyResult#REJECTED}.
   */
  public VerifyResult verifyAndSettle(String reference, String kind, UUID recordId) {
    String ref = reference == null ? "" : reference.trim();
    if (ref.isEmpty() || !properties.hasSecretKey() || !isOwnReference(ref)) {
      return VerifyResult.NOT_CHECKED;
    }
    try {
      Optional<PaystackClient.Transaction> found = verifier.verify(ref);
      if (found.isEmpty()) {
        return VerifyResult.PENDING;
      }
      PaystackClient.Transaction t = found.get();
      if (!ref.equals(t.reference())) {
        log.warn("Paystack verify for reference {} answered for another reference; ignored", ref);
        return VerifyResult.REJECTED;
      }
      if (recordId != null && !metadataMatches(t, kind, recordId)) {
        log.warn("Paystack transaction {} does not carry this record's metadata; ignored", ref);
        return VerifyResult.REJECTED;
      }
      if (t.isAbandonedOrFailed()) {
        return VerifyResult.NOT_PAID;
      }
      if (!t.isSuccess()) {
        return VerifyResult.PENDING;
      }
      requireLiveOnProd(t);
      return switch (applyConfirmed(toConfirmed(t))) {
        case SETTLED -> VerifyResult.SETTLED;
        case ALREADY_SETTLED -> VerifyResult.ALREADY_SETTLED;
        case NOT_FOUND -> VerifyResult.REJECTED;
        case DUPLICATE -> VerifyResult.DUPLICATE;
        case NEEDS_REFUND -> VerifyResult.NEEDS_REFUND;
        case REFUNDING -> VerifyResult.REFUNDING;
      };
    } catch (PaystackClient.PaystackException e) {
      log.warn("Paystack verify failed for reference {}: {}", ref, e.getMessage());
      return VerifyResult.ERROR;
    } catch (IllegalArgumentException e) {
      log.warn("Paystack payment {} was not settled: {}", ref, e.getMessage());
      return VerifyResult.REJECTED;
    }
  }

  private static boolean metadataMatches(PaystackClient.Transaction t, String kind, UUID recordId) {
    return kind != null
        && kind.equalsIgnoreCase(t.metadata().get("kind"))
        && recordId.toString().equalsIgnoreCase(String.valueOf(t.metadata().get("id")).trim());
  }

  // ------------------------------------------------------------------ status (return page)

  /**
   * Return page status of an order: {@code kind, id, status, paymentMethod, onlinePaymentChannel, paid,
   * cancelled}. The caller proves it may see it with the customer's email or a payment reference of this
   * order (see {@link #authorizeStatus}). A pending order, or one cancelled by expiry, is verified with
   * Paystack first; {@code cancelled} is true when Paystack reports the checkout abandoned or failed.
   */
  public Map<String, Object> orderStatus(UUID tenantId, UUID orderId, String customerEmail, String reference) {
    OrderEntity o = requireOrder(tenantId, orderId);
    boolean awaiting =
        OnlinePaymentMethods.isOnline(o.paymentMethod)
            && (o.status == OrderEntity.OrderStatus.pending_payment
                || (o.status == OrderEntity.OrderStatus.cancelled && o.expiredAt != null));
    String ref =
        authorizeStatus(
            OnlinePaymentAttemptEntity.Kind.ORDER, o.id, o.customerEmail, o.onlinePaymentReference, customerEmail,
            reference);
    VerifyResult verified = VerifyResult.NOT_CHECKED;
    OrderEntity current = o;
    if (awaiting) {
      verified = verifyAndSettle(ref, OnlinePaymentSettlementService.KIND_ORDER, o.id);
      if (verified.isPaid()) {
        current = requireOrder(tenantId, orderId);
      }
    }
    boolean paid = current.status == OrderEntity.OrderStatus.paid;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", OnlinePaymentSettlementService.KIND_ORDER);
    out.put("id", current.id.toString());
    out.put("status", current.status.name());
    out.put("paymentMethod", current.paymentMethod == null ? "" : current.paymentMethod.name());
    out.put("onlinePaymentChannel", current.onlinePaymentChannel == null ? "" : current.onlinePaymentChannel.name());
    out.put("paid", paid);
    out.put("cancelled", !paid && verified == VerifyResult.NOT_PAID);
    // The return page doubles as the confirmation: show the code staff will ask for at handover.
    String handoverCode = paid ? HandoverCodes.forCustomer(current) : null;
    if (handoverCode != null) out.put("handoverCode", handoverCode);
    return out;
  }

  /**
   * Same shape and rules as {@link #orderStatus}, for a salon booking. For a pay-in-store booking with a booking
   * fee, {@code paid} means the fee was paid, and {@code bookingFee} / {@code receiptLines} describe it. A fee
   * booking cancelled before its fee was paid is still verified, so a late payment is found and refunded.
   */
  public Map<String, Object> bookingStatus(UUID tenantId, UUID bookingId, String customerEmail, String reference) {
    SalonBookingEntity b = requireBooking(tenantId, bookingId);
    boolean feeBooking = b.hasBookingFee();
    boolean awaiting =
        feeBooking
            ? b.bookingFeeStatus == SalonBookingEntity.BookingFeeStatus.awaiting_payment
                || b.bookingFeeStatus == SalonBookingEntity.BookingFeeStatus.not_paid
            : OnlinePaymentMethods.isOnline(b.clientPaymentMethod) && b.status == SalonBookingEntity.Status.pending;
    String ref =
        authorizeStatus(
            OnlinePaymentAttemptEntity.Kind.BOOKING, b.id, b.customerEmail, b.onlinePaymentReference, customerEmail,
            reference);
    VerifyResult verified = VerifyResult.NOT_CHECKED;
    SalonBookingEntity current = b;
    if (awaiting) {
      verified = verifyAndSettle(ref, OnlinePaymentSettlementService.KIND_BOOKING, b.id);
      if (verified.isPaid() || verified == VerifyResult.REFUNDING) {
        current = requireBooking(tenantId, bookingId);
      }
    }
    boolean paid =
        feeBooking
            ? BookingFeeService.feePaid(current)
            : current.status == SalonBookingEntity.Status.confirmed
                || current.status == SalonBookingEntity.Status.in_progress
                || current.status == SalonBookingEntity.Status.completed;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", OnlinePaymentSettlementService.KIND_BOOKING);
    out.put("id", current.id.toString());
    out.put("status", current.status.name());
    out.put("paymentMethod", current.clientPaymentMethod == null ? "" : current.clientPaymentMethod.name());
    out.put("onlinePaymentChannel", current.onlinePaymentChannel == null ? "" : current.onlinePaymentChannel.name());
    out.put("paid", paid);
    out.put("cancelled", !paid && verified == VerifyResult.NOT_PAID);
    // The return page doubles as the confirmation: show the code staff will ask for when the service starts.
    String handoverCode = paid ? HandoverCodes.forCustomer(current) : null;
    if (handoverCode != null) out.put("handoverCode", handoverCode);
    if (feeBooking) {
      out.put("paymentPurpose", "booking_fee");
      SalonBookingService.putBookingFeeView(out, current, Instant.now());
    }
    return out;
  }

  /**
   * Return page status of a merchant subscription payment: {@code paymentId, status, paid, cancelled}. A
   * still pending payment is verified with Paystack and settled through the same idempotent path as the
   * webhook. Another tenant's payment is {@code not_found}; a {@code reference} other than this payment's
   * is {@code reference_mismatch}.
   */
  public Map<String, Object> subscriptionPaymentStatus(UUID tenantId, UUID paymentId, String reference) {
    SubscriptionOnlinePaymentEntity p = requireSubscriptionPayment(tenantId, paymentId);
    String given = reference == null ? "" : reference.trim();
    if (!given.isEmpty() && !given.equals(p.onlinePaymentReference)) {
      throw new IllegalArgumentException("reference_mismatch");
    }
    VerifyResult verified = VerifyResult.NOT_CHECKED;
    SubscriptionOnlinePaymentEntity current = p;
    if (SubscriptionOnlinePaymentEntity.STATUS_PENDING.equalsIgnoreCase(p.status)) {
      verified = verifyAndSettle(p.onlinePaymentReference, OnlinePaymentSettlementService.KIND_SUBSCRIPTION, p.id);
      if (verified.isPaid()) {
        current = requireSubscriptionPayment(tenantId, paymentId);
      }
    }
    boolean paid = SubscriptionOnlinePaymentEntity.STATUS_COMPLETED.equalsIgnoreCase(current.status);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("paymentId", current.id.toString());
    out.put("status", current.status);
    out.put("paid", paid);
    out.put("cancelled", !paid && verified == VerifyResult.NOT_PAID);
    return out;
  }

  /**
   * Who may see an order/booking payment status: the customer's email, or a payment reference of this
   * record: the stored (latest) one or any attempt the ledger holds for this record. A made up reference is
   * refused without asking Paystack; Paystack is only asked while the record awaits payment. At least one
   * of email and reference is required.
   *
   * @return the reference to verify: the given one, else the stored one
   */
  private String authorizeStatus(
      OnlinePaymentAttemptEntity.Kind kind,
      UUID recordId,
      String storedEmail,
      String storedReference,
      String customerEmail,
      String reference) {
    String email = customerEmail == null ? "" : customerEmail.trim();
    String ref = reference == null ? "" : reference.trim();
    if (email.isEmpty() && ref.isEmpty()) {
      throw new IllegalArgumentException("email_or_reference_required");
    }
    if (!email.isEmpty() && !email.equalsIgnoreCase(storedEmail == null ? "" : storedEmail.trim())) {
      throw new IllegalArgumentException("email_mismatch");
    }
    if (ref.isEmpty()) {
      return storedReference;
    }
    if (ref.equals(storedReference)) {
      return ref;
    }
    boolean inLedger =
        isOwnReference(ref)
            && attempts.findByKindAndRecordIdAndReference(kind, recordId, ref).isPresent();
    if (!inLedger) {
      throw new IllegalArgumentException("reference_mismatch");
    }
    return ref;
  }

  private OrderEntity requireOrder(UUID tenantId, UUID orderId) {
    OrderEntity o = orders.findOneByTenantAndId(tenantId, orderId);
    if (o == null) throw new IllegalArgumentException("not_found");
    return o;
  }

  private SalonBookingEntity requireBooking(UUID tenantId, UUID bookingId) {
    SalonBookingEntity b = bookings.findOneByTenantAndId(tenantId, bookingId);
    if (b == null) throw new IllegalArgumentException("not_found");
    return b;
  }

  private SubscriptionOnlinePaymentEntity requireSubscriptionPayment(UUID tenantId, UUID paymentId) {
    return subscriptionPayments
        .findById(paymentId)
        .filter(p -> tenantId.equals(p.tenantId))
        .orElseThrow(() -> new IllegalArgumentException("not_found"));
  }

  private BigDecimal bookingPayable(SalonBookingEntity b) {
    SalonServiceEntity svc =
        salonServices
            .findByIdAndTenantId(b.serviceId, b.tenantId)
            .orElseThrow(() -> new IllegalArgumentException("service_not_found"));
    return OnlinePaymentSettlementService.bookingPayable(b, svc);
  }

  // ------------------------------------------------------------------ helpers

  private void requireShopAcceptsOnline(UUID tenantId) {
    ShopSettingsEntity s = shopSettings.findByTenantId(tenantId).orElse(null);
    if (!CustomerPaymentPolicy.acceptOnline(s)) {
      throw new IllegalArgumentException("online_payment_not_accepted");
    }
  }

  /** Paystack needs a customer email; for subscriptions that is the merchant owner's login email. */
  private String ownerEmail(UUID tenantId) {
    return memberships.findAllByTenantIdAndRole(tenantId, Role.MERCHANT_OWNER).stream()
        .sorted(Comparator.comparing((MembershipEntity m) -> m.createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .map(m -> users.findById(m.userId).map(u -> u.email).orElse(null))
        .filter(Emails::isValid)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("owner_email_missing"));
  }

  /**
   * A short readable reference (it can be shown to the customer), new for every checkout attempt: the
   * prefix plus 16 random upper case letters and digits. Paystack allows letters, digits, {@code -},
   * {@code .} and {@code =}.
   */
  static String newReference(String prefix) {
    StringBuilder sb = new StringBuilder(prefix.length() + REFERENCE_RANDOM_LENGTH).append(prefix);
    for (int i = 0; i < REFERENCE_RANDOM_LENGTH; i++) {
      sb.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
    }
    return sb.toString();
  }

  private String frontendUrl(String path) {
    String base = frontendBaseUrl;
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return path.startsWith("/") ? base + path : base + "/" + path;
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
  }
}
