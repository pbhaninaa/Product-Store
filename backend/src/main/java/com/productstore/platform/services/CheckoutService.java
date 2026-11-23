package com.productstore.platform.services;

import com.productstore.platform.config.CustomerPaymentPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.util.RefCodes;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.OrderItemEntity;
import com.productstore.platform.entities.OnlinePaymentChannel;
import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.util.Emails;
import com.productstore.platform.util.OnlinePaymentMethods;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CheckoutService {
  private final ProductRepository products;
  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final ShopSettingsRepository shopSettings;
  private final EftProofDocumentAnalyzer eftProofAnalyzer;
  private final MerchantNotificationService notifications;
  private final PromotionService promotions;
  private final OnlinePaymentAvailability onlinePayments;

  public CheckoutService(
      ProductRepository products,
      OrderRepository orders,
      OrderItemRepository orderItems,
      ShopSettingsRepository shopSettings,
      EftProofDocumentAnalyzer eftProofAnalyzer,
      MerchantNotificationService notifications,
      PromotionService promotions,
      OnlinePaymentAvailability onlinePayments) {
    this.products = products;
    this.orders = orders;
    this.orderItems = orderItems;
    this.shopSettings = shopSettings;
    this.eftProofAnalyzer = eftProofAnalyzer;
    this.notifications = notifications;
    this.promotions = promotions;
    this.onlinePayments = onlinePayments;
  }

  public record CreateOrderLine(UUID productId, int quantity) {}

  public record CreateOrderCommand(
      String customerName,
      String customerEmail,
      String customerPhone,
      OrderEntity.DeliveryType deliveryType,
      String deliveryAddress,
      Double deliveryLat,
      Double deliveryLng,
      OrderEntity.PaymentMethod paymentMethod,
      OnlinePaymentChannel onlinePaymentChannel,
      List<CreateOrderLine> items,
      UUID promoId,
      UUID clientUserId) {}

  public record CreateOrderResult(UUID orderId, boolean needsEftProof, String cashPaymentCode) {}

  /**
   * Quotes the promotion a cart would get at checkout (best valid offer for the store), using the
   * server's current prices. Lines for unknown or archived products are ignored.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> quotePromotion(UUID tenantId, List<CreateOrderLine> items) {
    Map<UUID, Integer> qtyByProduct = new HashMap<>();
    if (items != null) {
      for (CreateOrderLine l : items) {
        if (l == null || l.productId() == null || l.quantity() < 1 || l.quantity() > 100) continue;
        qtyByProduct.merge(l.productId(), l.quantity(), Integer::sum);
      }
    }
    if (qtyByProduct.isEmpty() || qtyByProduct.size() > 50) {
      return promotions.toQuoteMap(java.util.Optional.empty(), BigDecimal.ZERO);
    }
    List<ProductEntity> dbProducts =
        products.findActiveByTenantAndIds(tenantId, qtyByProduct.keySet().stream().toList());
    List<CreateOrderLine> known = new ArrayList<>();
    for (ProductEntity p : dbProducts) {
      known.add(new CreateOrderLine(p.id, qtyByProduct.get(p.id)));
    }
    List<PromotionService.PricedLine> lines = pricedLines(known, dbProducts);
    BigDecimal subtotal = BigDecimal.ZERO;
    for (PromotionService.PricedLine l : lines) subtotal = subtotal.add(l.amount());
    return promotions.toQuoteMap(promotions.bestFor(tenantId, lines), subtotal);
  }

  private static List<PromotionService.PricedLine> pricedLines(
      List<CreateOrderLine> items, List<ProductEntity> dbProducts) {
    List<PromotionService.PricedLine> out = new ArrayList<>();
    for (CreateOrderLine l : items) {
      ProductEntity p =
          dbProducts.stream().filter(x -> x.id.equals(l.productId())).findFirst().orElse(null);
      if (p == null || p.priceZar == null) continue;
      BigDecimal line =
          p.priceZar.multiply(BigDecimal.valueOf(l.quantity())).setScale(2, RoundingMode.HALF_UP);
      out.add(new PromotionService.PricedLine(p.id, p.category, line));
    }
    return out;
  }

  @Transactional
  public CreateOrderResult createOrder(UUID tenantId, CreateOrderCommand cmd) {
    String name = safeTrim(cmd.customerName());
    String email = safeTrim(cmd.customerEmail()).toLowerCase();
    if (name.length() < 2) throw new IllegalArgumentException("invalid_name");
    if (!Emails.isValid(email)) throw new IllegalArgumentException("invalid_email");
    if (cmd.deliveryType() == null) throw new IllegalArgumentException("invalid_delivery_type");
    if (cmd.paymentMethod() == null) throw new IllegalArgumentException("invalid_payment_method");
    if (OnlinePaymentMethods.isOnline(cmd.paymentMethod()) && cmd.onlinePaymentChannel() == null) {
      throw new IllegalArgumentException("online_payment_channel_required");
    }
    if (!OnlinePaymentMethods.isOnline(cmd.paymentMethod()) && cmd.onlinePaymentChannel() != null) {
      throw new IllegalArgumentException("online_payment_channel_not_applicable");
    }
    if (cmd.items() == null || cmd.items().isEmpty()) throw new IllegalArgumentException("empty_cart");
    if (cmd.items().size() > 50) throw new IllegalArgumentException("too_many_lines");

    if (cmd.deliveryType() == OrderEntity.DeliveryType.delivery) {
      if (safeTrim(cmd.deliveryAddress()).length() < 6) throw new IllegalArgumentException("delivery_address_required");
    }

    Map<UUID, Integer> qtyByProduct = new HashMap<>();
    for (CreateOrderLine l : cmd.items()) {
      if (l == null || l.productId() == null) throw new IllegalArgumentException("invalid_line");
      int q = l.quantity();
      if (q < 1 || q > 100) throw new IllegalArgumentException("invalid_line");
      qtyByProduct.merge(l.productId(), q, Integer::sum);
      if (qtyByProduct.get(l.productId()) > 100) throw new IllegalArgumentException("invalid_line");
    }

    // Row locks make concurrent checkouts for the same products take turns, so the "stock minus unpaid
    // reservations" check below cannot pass for two orders that together exceed the stock.
    List<ProductEntity> dbProducts =
        products.lockActiveByTenantAndIds(tenantId, qtyByProduct.keySet().stream().toList());
    if (dbProducts.size() != qtyByProduct.size()) throw new IllegalArgumentException("product_not_found");

    for (ProductEntity p : dbProducts) {
      long pending = orderItems.sumPendingQtyForProduct(tenantId, p.id);
      int requested = qtyByProduct.getOrDefault(p.id, 0);
      int available = Math.max(0, (p.stock == null ? 0 : p.stock) - (int) pending);
      if (available < requested) throw new IllegalArgumentException("insufficient_stock");
    }

    BigDecimal subtotal = BigDecimal.ZERO;
    for (CreateOrderLine l : cmd.items()) {
      ProductEntity p =
          dbProducts.stream().filter(x -> x.id.equals(l.productId())).findFirst().orElseThrow();
      BigDecimal unit = p.priceZar;
      BigDecimal line = unit.multiply(BigDecimal.valueOf(l.quantity())).setScale(2, RoundingMode.HALF_UP);
      subtotal = subtotal.add(line);
    }
    if (subtotal.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("empty_cart");

    var settings = shopSettings.findByTenantId(tenantId).orElse(null);
    boolean allowOnline = CustomerPaymentPolicy.acceptOnline(settings);
    boolean allowEft = CustomerPaymentPolicy.acceptEft(settings);
    boolean allowCash = CustomerPaymentPolicy.acceptCash(settings);
    if (OnlinePaymentMethods.isOnline(cmd.paymentMethod()) && !allowOnline) {
      throw new IllegalArgumentException("online_payment_not_accepted");
    }
    if (OnlinePaymentMethods.isOnline(cmd.paymentMethod())) {
      // Checked before anything is saved, so an unavailable provider never leaves an order holding stock.
      onlinePayments.requireAvailable();
    }
    if (cmd.paymentMethod() == OrderEntity.PaymentMethod.eft && !allowEft) {
      throw new IllegalArgumentException("eft_not_accepted");
    }
    if (cmd.paymentMethod() == OrderEntity.PaymentMethod.cash_store && !allowCash) {
      throw new IllegalArgumentException("cash_not_accepted");
    }
    BigDecimal deliveryFee = BigDecimal.ZERO;
    Double deliveryDistanceKm = null;
    if (cmd.deliveryType() == OrderEntity.DeliveryType.delivery && settings != null) {
      BigDecimal flat = settings.deliveryFeeFlatZar == null ? BigDecimal.ZERO : settings.deliveryFeeFlatZar;
      if ("per_km".equalsIgnoreCase(safeTrim(settings.deliveryFeeMode))
          && settings.storeLat != null
          && settings.storeLng != null) {
        // The fee comes from the customer's pin, so the pin is required and is where the order is delivered
        // (merchants see it on the order). The flat fee is the minimum, so a pin dropped on the store is not free.
        if (!validCoordinate(cmd.deliveryLat, cmd.deliveryLng)) {
          throw new IllegalArgumentException("delivery_location_required");
        }
        double km = haversineKm(settings.storeLat, settings.storeLng, cmd.deliveryLat, cmd.deliveryLng);
        deliveryDistanceKm = Math.round(km * 10) / 10.0;
        BigDecimal perKm =
            settings.deliveryFeePerKmZar == null ? BigDecimal.ZERO : settings.deliveryFeePerKmZar;
        deliveryFee = BigDecimal.valueOf(km).multiply(perKm).max(flat);
      } else {
        deliveryFee = flat;
      }
      deliveryFee = deliveryFee.setScale(2, RoundingMode.HALF_UP);
    }

    // Promotions: an explicit promoId is validated (tenant, dates, usage, minimum, categories); with no
    // promoId the best currently-valid promotion is applied automatically. Prices come from the DB.
    // The use is claimed now (atomically, against the limit) and given back if the order never gets paid.
    var promoQuote = promotions.resolve(tenantId, cmd.promoId(), pricedLines(cmd.items(), dbProducts));
    BigDecimal discount = promoQuote.map(PromotionService.PromotionQuote::discount).orElse(BigDecimal.ZERO);
    UUID promotionId = promoQuote.map(q -> promotions.claimUse(q.promo())).orElse(null);
    BigDecimal total = subtotal.add(deliveryFee).subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

    UUID orderId = UUID.randomUUID();
    OrderEntity o = new OrderEntity();
    o.id = orderId;
    o.tenantId = tenantId;
    o.createdAt = Instant.now();
    o.customerName = name;
    o.customerEmail = email;
    o.customerPhone = safeTrim(cmd.customerPhone());
    o.deliveryType = cmd.deliveryType();
    o.deliveryAddress = cmd.deliveryType() == OrderEntity.DeliveryType.delivery ? safeTrim(cmd.deliveryAddress()) : null;
    o.deliveryLat = cmd.deliveryLat;
    o.deliveryLng = cmd.deliveryLng;
    o.deliveryFeeZar = deliveryFee;
    o.deliveryDistanceKm = deliveryDistanceKm;
    o.promotionId = promotionId;
    o.paymentMethod = cmd.paymentMethod();
    o.onlinePaymentChannel = cmd.onlinePaymentChannel();
    o.clientUserId = cmd.clientUserId();
    o.status = OrderEntity.OrderStatus.pending_payment;
    if (cmd.paymentMethod() == OrderEntity.PaymentMethod.eft) {
      o.paymentVerificationState = OrderEntity.PaymentVerificationState.awaiting_proof;
      o.cashPaymentCode = null;
    } else if (cmd.paymentMethod() == OrderEntity.PaymentMethod.cash_store) {
      o.paymentVerificationState = OrderEntity.PaymentVerificationState.not_applicable;
      o.cashPaymentCode = generateCashPaymentCode();
    } else if (OnlinePaymentMethods.isOnline(cmd.paymentMethod())) {
      o.paymentVerificationState = OrderEntity.PaymentVerificationState.not_applicable;
      o.cashPaymentCode = null;
    } else {
      throw new IllegalArgumentException("invalid_payment_method");
    }
    o.subtotalZar = subtotal;
    o.totalZar = total;
    orders.save(o);

    for (CreateOrderLine l : cmd.items()) {
      ProductEntity p =
          dbProducts.stream().filter(x -> x.id.equals(l.productId())).findFirst().orElseThrow();
      BigDecimal unit = p.priceZar.setScale(2, RoundingMode.HALF_UP);
      BigDecimal line = unit.multiply(BigDecimal.valueOf(l.quantity())).setScale(2, RoundingMode.HALF_UP);

      OrderItemEntity oi = new OrderItemEntity();
      oi.id = UUID.randomUUID();
      oi.tenantId = tenantId;
      oi.orderId = orderId;
      oi.productId = p.id;
      oi.quantity = l.quantity();
      oi.unitPriceZar = unit;
      oi.lineTotalZar = line;
      orderItems.save(oi);
    }

    boolean needsEft = cmd.paymentMethod() == OrderEntity.PaymentMethod.eft;
    String cashOut = cmd.paymentMethod() == OrderEntity.PaymentMethod.cash_store ? o.cashPaymentCode : null;
    notifications.notifyOrderPlaced(tenantId, o);
    return new CreateOrderResult(orderId, needsEft, cashOut);
  }

  /** Marks a pending online order as paid after the payment provider confirmed it. */
  @Transactional
  public void finalizeOnlinePaidOrder(OrderEntity o) {
    if (o == null) throw new IllegalArgumentException("invalid_order");
    if (!OnlinePaymentMethods.isOnline(o.paymentMethod)) {
      throw new IllegalArgumentException("not_online_order");
    }
    if (o.status == OrderEntity.OrderStatus.paid) return;
    if (o.expiredAt != null && o.status == OrderEntity.OrderStatus.cancelled) {
      // The customer paid after we expired the order for being unpaid. The money is real, so take the order
      // back and count the promotion again instead of leaving a paid customer with a cancelled order.
      o.status = OrderEntity.OrderStatus.pending_payment;
      o.cancelledAt = null;
      o.expiredAt = null;
      promotions.forceUse(o.promotionId);
    }
    if (o.status != OrderEntity.OrderStatus.pending_payment) {
      throw new IllegalArgumentException("order_not_pending");
    }
    if (o.cancelledAt != null) throw new IllegalArgumentException("order_cancelled");
    finalizePaidOrder(o.tenantId, o);
  }

  /**
   * Customer uploads EFT proof; reference auto-matched to order id → paid + stock; otherwise payment stays pending for
   * merchant manual confirmation (see {@link #confirmPayment}).
   */
  @Transactional
  public Map<String, Object> submitOrderEftProof(
      UUID tenantId, UUID orderId, String customerEmail, String bankReference, MultipartFile proofFile)
      throws Exception {
    if (orderId == null) throw new IllegalArgumentException("invalid_order");
    if (proofFile == null || proofFile.isEmpty()) throw new IllegalArgumentException("proof_required");
    String email = safeTrim(customerEmail).toLowerCase();
    if (!Emails.isValid(email)) throw new IllegalArgumentException("invalid_email");
    String ref = safeTrim(bankReference);
    if (ref.length() < 3) throw new IllegalArgumentException("invalid_reference");

    OrderEntity o = lockOrder(tenantId, orderId);
    if (o == null) throw new IllegalArgumentException("not_found");
    if (!email.equalsIgnoreCase(safeTrim(o.customerEmail))) throw new IllegalArgumentException("email_mismatch");
    if (o.paymentMethod != OrderEntity.PaymentMethod.eft) {
      throw new IllegalArgumentException("not_eft_order");
    }
    if (o.status != OrderEntity.OrderStatus.pending_payment) {
      throw new IllegalArgumentException("order_not_pending");
    }
    if (o.cancelledAt != null) throw new IllegalArgumentException("order_cancelled");
    if (o.paymentVerificationState != OrderEntity.PaymentVerificationState.awaiting_proof) {
      throw new IllegalArgumentException("proof_not_expected");
    }
    if (o.paymentProofPath != null && !o.paymentProofPath.isBlank()) {
      throw new IllegalArgumentException("proof_already_submitted");
    }

    byte[] payload = proofFile.getBytes();
    String ext = eftProofAnalyzer.resolveProofUploadExtension(proofFile.getOriginalFilename(), payload);
    o.paymentProofData = payload;
    o.paymentProofContentType = ext.equals("pdf") ? "application/pdf" : "image/" + ext;
    o.paymentProofPath = "";
    o.paymentReferenceDeclared = ref;

    // Never auto-confirm: a customer-supplied PDF proves nothing about money arriving (anyone can type
    // the total, today's date and their own reference into one). The merchant checks their bank.
    boolean autoOk = false;
    if (autoOk) {
      o.paymentVerificationState = OrderEntity.PaymentVerificationState.auto_verified;
      finalizePaidOrder(tenantId, o);
    } else {
      o.paymentVerificationState = OrderEntity.PaymentVerificationState.manual_pending;
      orders.save(o);
      notifications.notifyOrderNeedsManualEftReview(tenantId, o);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("paymentVerificationState", o.paymentVerificationState.name());
    out.put("orderStatus", o.status.name());
    out.put("autoVerified", autoOk);
    out.put("manualVerificationRequired", !autoOk);
    out.put("proofType", ext);
    out.put(
        "autoVerifyMode",
        "manual_review");
    return out;
  }

  /**
   * Marks an order paid. EFT: only after manual review ({@code manual_pending}). Cash in store: {@code cashCode} must
   * match the code issued when the order was placed.
   */
  @Transactional
  public boolean confirmPayment(UUID tenantId, UUID orderId, String cashCode, UUID completedByEmployeeId) {
    OrderEntity o = lockOrder(tenantId, orderId);
    if (o == null) return false;
    if (o.status != OrderEntity.OrderStatus.pending_payment) return false;
    if (o.cancelledAt != null) return false;

    if (o.paymentMethod == OrderEntity.PaymentMethod.eft) {
      if (o.paymentVerificationState != OrderEntity.PaymentVerificationState.manual_pending) {
        return false;
      }
      assertEftProofSafeToApprove(o);
    } else if (o.paymentMethod == OrderEntity.PaymentMethod.cash_store) {
      if (o.cashPaymentCode == null || o.cashPaymentCode.isBlank()) {
        return false;
      }
      if (!CashCodeGuard.check("order:" + o.id, o.cashPaymentCode, cashCode)) {
        return false;
      }
    } else {
      return false;
    }

    finalizePaidOrder(tenantId, o, completedByEmployeeId);
    return true;
  }

  private void assertEftProofSafeToApprove(OrderEntity o) {
    if (o.paymentProofData == null || o.paymentProofData.length < 32) {
      throw new IllegalArgumentException("proof_missing");
    }
    // Images stay manual-only — amount/ref cannot be re-checked from pixels.
    if (!eftProofAnalyzer.isPdfMagic(o.paymentProofData)) {
      return;
    }
    BigDecimal expected =
        o.totalZar == null ? BigDecimal.ZERO : o.totalZar.setScale(2, RoundingMode.HALF_UP);
    String ref = o.paymentReferenceDeclared == null ? "" : o.paymentReferenceDeclared;
    if (!eftProofAnalyzer.verifyPdfAmountAndReference(o.paymentProofData, expected, ref)) {
      throw new IllegalArgumentException("eft_proof_amount_or_reference_mismatch");
    }
  }

  private void finalizePaidOrder(UUID tenantId, OrderEntity o) {
    finalizePaidOrder(tenantId, o, null);
  }

  private void finalizePaidOrder(UUID tenantId, OrderEntity o, UUID completedByEmployeeId) {
    var lines = orderItems.findAllByTenantAndOrderId(tenantId, o.id);
    for (var line : lines) {
      // One conditional UPDATE per line: two payments landing together can no longer both read the same
      // stock and oversell. Any failure rolls the whole confirmation back.
      if (products.deductStock(tenantId, line.productId, line.quantity) == 0) {
        throw new IllegalArgumentException("insufficient_stock_on_confirm");
      }
    }

    o.status = OrderEntity.OrderStatus.paid;
    o.fulfillmentStatus = OrderEntity.FulfillmentStatus.processing;
    o.paymentConfirmedAt = Instant.now();
    o.completedAt = o.paymentConfirmedAt;
    if (completedByEmployeeId != null) {
      o.completedByEmployeeId = completedByEmployeeId;
    }
    orders.save(o);
    notifications.notifyOrderPaid(tenantId, o);
  }

  /**
   * Merchant fulfilment after payment (Wheel Hub IN_PROGRESS → COMPLETED, with an extra ready/out-for-delivery step).
   * Payment status is never changed here.
   */
  @Transactional
  public Map<String, Object> updateFulfillment(UUID tenantId, UUID orderId, String statusRaw) {
    OrderEntity o = orders.findOneByTenantAndId(tenantId, orderId);
    if (o == null) throw new IllegalArgumentException("not_found");
    if (o.status != OrderEntity.OrderStatus.paid) {
      throw new IllegalArgumentException("order_not_paid");
    }
    OrderEntity.FulfillmentStatus to;
    try {
      to = OrderEntity.FulfillmentStatus.valueOf(safeTrim(statusRaw).toLowerCase(Locale.ROOT));
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid_fulfillment_status");
    }
    OrderEntity.FulfillmentStatus from = OrderEntity.effectiveFulfillment(o);
    assertFulfillmentTransition(from, to);
    o.fulfillmentStatus = to;
    orders.save(o);
    notifications.notifyOrderFulfillmentChanged(tenantId, o);
    return toPublicOrderMap(tenantId, o, false);
  }

  public Map<String, Object> listAdminOrders(UUID tenantId) {
    var rows = orders.findAllByTenant(tenantId);
    List<UUID> ids = rows.stream().map(o -> o.id).toList();
    Map<UUID, List<OrderItemEntity>> itemsByOrder = new HashMap<>();
    if (!ids.isEmpty()) {
      for (OrderItemEntity line : orderItems.findAllByTenantIdAndOrderIdIn(tenantId, ids)) {
        itemsByOrder.computeIfAbsent(line.orderId, k -> new ArrayList<>()).add(line);
      }
    }
    Map<UUID, String> productNames = productNames(tenantId, itemsByOrder);
    List<Map<String, Object>> payload = new ArrayList<>();
    for (OrderEntity o : rows) {
      payload.add(toAdminOrderMap(o, itemsByOrder.getOrDefault(o.id, List.of()), productNames, true));
    }
    return Map.of("orders", payload);
  }

  /** Track page: {@code reference} is the short order reference people see, or the full id from a link. */
  public Map<String, Object> lookupPublic(UUID tenantId, String reference, String customerEmail) {
    String ref = safeTrim(reference);
    UUID fullId = RefCodes.parseUuidOrNull(ref);
    if (fullId != null) return lookupPublic(tenantId, fullId, customerEmail);
    if (!RefCodes.isShortRef(ref)) throw new IllegalArgumentException("invalid_order");
    String email = safeTrim(customerEmail).toLowerCase(Locale.ROOT);
    if (!Emails.isValid(email)) throw new IllegalArgumentException("invalid_email");
    OrderEntity o =
        orders.findByTenantIdAndCustomerEmailIgnoreCase(tenantId, email).stream()
            .filter(x -> RefCodes.isShortRefOf(ref, x.id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("not_found"));
    return toPublicOrderMap(tenantId, o, true);
  }

  public Map<String, Object> lookupPublic(UUID tenantId, UUID orderId, String customerEmail) {
    if (orderId == null) throw new IllegalArgumentException("invalid_order");
    String email = safeTrim(customerEmail).toLowerCase(Locale.ROOT);
    if (!Emails.isValid(email)) throw new IllegalArgumentException("invalid_email");
    OrderEntity o = orders.findOneByTenantAndId(tenantId, orderId);
    if (o == null) throw new IllegalArgumentException("not_found");
    if (!email.equalsIgnoreCase(safeTrim(o.customerEmail))) throw new IllegalArgumentException("not_found");
    return toPublicOrderMap(tenantId, o, true);
  }

  public Map<String, Object> toPublicOrderMap(UUID tenantId, OrderEntity o, boolean includeCashCodeIfPending) {
    var lines = orderItems.findAllByTenantAndOrderId(tenantId, o.id);
    Map<UUID, List<OrderItemEntity>> grouped = Map.of(o.id, lines);
    Map<UUID, String> names = productNames(tenantId, grouped);
    Map<String, Object> m = toAdminOrderMap(o, lines, names, false);
    if (includeCashCodeIfPending
        && o.status == OrderEntity.OrderStatus.pending_payment
        && o.paymentMethod == OrderEntity.PaymentMethod.cash_store
        && o.cashPaymentCode != null
        && !o.cashPaymentCode.isBlank()) {
      m.put("cashPaymentCode", o.cashPaymentCode);
    }
    return m;
  }

  private Map<String, Object> toAdminOrderMap(
      OrderEntity o,
      List<OrderItemEntity> lines,
      Map<UUID, String> productNames,
      boolean includeStaffFields) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", o.id.toString());
    m.put("createdAt", o.createdAt.toString());
    m.put("created_at", o.createdAt.toString());
    m.put("customerName", o.customerName);
    m.put("customer_name", o.customerName);
    m.put("customerEmail", o.customerEmail);
    m.put("customer_email", o.customerEmail);
    m.put("customerPhone", nz(o.customerPhone));
    m.put("customer_phone", nz(o.customerPhone));
    m.put("deliveryType", o.deliveryType.name());
    m.put("delivery_type", o.deliveryType.name());
    m.put("deliveryAddress", nz(o.deliveryAddress));
    m.put("delivery_address", nz(o.deliveryAddress));
    if (o.deliveryType == OrderEntity.DeliveryType.delivery && validCoordinate(o.deliveryLat, o.deliveryLng)) {
      m.put(
          "deliveryPinUrl",
          "https://www.google.com/maps/search/?api=1&query=" + o.deliveryLat + "," + o.deliveryLng);
      if (o.deliveryDistanceKm != null) m.put("deliveryDistanceKm", o.deliveryDistanceKm);
    }
    if (o.expiredAt != null) m.put("expiredAt", o.expiredAt.toString());
    m.put("paymentMethod", o.paymentMethod.name());
    m.put("payment_method", o.paymentMethod.name());
    m.put("onlinePaymentChannel", o.onlinePaymentChannel == null ? "" : o.onlinePaymentChannel.name());
    m.put("paymentVerificationState", o.paymentVerificationState.name());
    m.put("payment_verification_state", o.paymentVerificationState.name());
    m.put("status", o.status.name());
    OrderEntity.FulfillmentStatus fulfillment = OrderEntity.effectiveFulfillment(o);
    m.put("fulfillmentStatus", fulfillment == null ? "" : fulfillment.name());
    m.put("fulfillment_status", fulfillment == null ? "" : fulfillment.name());
    m.put("subtotalZar", o.subtotalZar.toPlainString());
    m.put("subtotal_zar", o.subtotalZar.toPlainString());
    m.put("deliveryFeeZar", o.deliveryFeeZar.toPlainString());
    m.put("delivery_fee_zar", o.deliveryFeeZar.toPlainString());
    m.put("totalZar", o.totalZar.toPlainString());
    m.put("total_zar", o.totalZar.toPlainString());
    // The order row keeps subtotal, fee and total; any promotion discount is the difference.
    BigDecimal orderDiscount =
        o.subtotalZar
            .add(o.deliveryFeeZar == null ? BigDecimal.ZERO : o.deliveryFeeZar)
            .subtract(o.totalZar)
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);
    m.put("discountZar", orderDiscount.toPlainString());
    m.put("discount_zar", orderDiscount.toPlainString());
    if (o.paymentConfirmedAt != null) {
      m.put("paymentConfirmedAt", o.paymentConfirmedAt.toString());
      m.put("payment_confirmed_at", o.paymentConfirmedAt.toString());
    }
    if (includeStaffFields) {
      if (o.completedByEmployeeId != null) {
        m.put("completedByEmployeeId", o.completedByEmployeeId.toString());
      }
      if (o.completedAt != null) {
        m.put("completedAt", o.completedAt.toString());
      }
    }
    List<Map<String, Object>> itemPayload = new ArrayList<>();
    for (OrderItemEntity line : lines) {
      Map<String, Object> it = new LinkedHashMap<>();
      it.put("productId", line.productId.toString());
      it.put("product_id", line.productId.toString());
      it.put("quantity", line.quantity);
      it.put("unitPriceZar", line.unitPriceZar.toPlainString());
      it.put("unit_price_zar", line.unitPriceZar.toPlainString());
      it.put("lineTotalZar", line.lineTotalZar.toPlainString());
      it.put("line_total_zar", line.lineTotalZar.toPlainString());
      String pname = productNames.getOrDefault(line.productId, "Product");
      it.put("products", Map.of("name", pname));
      itemPayload.add(it);
    }
    m.put("items", itemPayload);
    m.put("order_items", itemPayload);
    return m;
  }

  private Map<UUID, String> productNames(UUID tenantId, Map<UUID, List<OrderItemEntity>> itemsByOrder) {
    List<UUID> pids = new ArrayList<>();
    for (List<OrderItemEntity> lines : itemsByOrder.values()) {
      for (OrderItemEntity line : lines) {
        pids.add(line.productId);
      }
    }
    Map<UUID, String> names = new HashMap<>();
    if (pids.isEmpty()) return names;
    for (ProductEntity p : products.findAllById(pids)) {
      if (p.tenantId.equals(tenantId)) names.put(p.id, p.name == null ? "Product" : p.name);
    }
    return names;
  }

  private static void assertFulfillmentTransition(
      OrderEntity.FulfillmentStatus from, OrderEntity.FulfillmentStatus to) {
    if (from == to) return;
    if (from == OrderEntity.FulfillmentStatus.processing
        && (to == OrderEntity.FulfillmentStatus.ready || to == OrderEntity.FulfillmentStatus.completed)) {
      return;
    }
    if (from == OrderEntity.FulfillmentStatus.ready && to == OrderEntity.FulfillmentStatus.completed) {
      return;
    }
    throw new IllegalArgumentException("invalid_fulfillment_status");
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  @Transactional
  public boolean cancelUnpaid(UUID tenantId, UUID orderId) {
    OrderEntity o = lockOrder(tenantId, orderId);
    if (o == null) return false;
    if (o.status != OrderEntity.OrderStatus.pending_payment) return false;
    if (o.cancelledAt != null) return false;

    o.status = OrderEntity.OrderStatus.cancelled;
    o.cancelledAt = Instant.now();
    releasePromotion(o);
    orders.save(o);
    return true;
  }

  /**
   * Undoes a just-placed online order whose payment could not be started: cancels it (so it no longer
   * reserves stock) and gives back its promotion use. Runs in its own transaction. Only an unpaid online order
   * is touched; anything else is left as it is.
   *
   * @return true when the order was cancelled now
   */
  @Transactional
  public boolean cancelUnstartedOnlineOrder(UUID tenantId, UUID orderId) {
    OrderEntity o = lockOrder(tenantId, orderId);
    if (o == null
        || !OnlinePaymentMethods.isOnline(o.paymentMethod)
        || o.status != OrderEntity.OrderStatus.pending_payment
        || o.cancelledAt != null) {
      return false;
    }
    o.status = OrderEntity.OrderStatus.cancelled;
    o.cancelledAt = Instant.now();
    releasePromotion(o);
    o.promotionId = null;
    orders.save(o);
    return true;
  }

  /** Gives back the promotion use an unpaid order was holding (once). */
  private void releasePromotion(OrderEntity o) {
    if (o.promotionId == null) return;
    promotions.releaseUse(o.promotionId);
  }

  /**
   * Cancels orders that stayed unpaid too long so they stop holding stock and promotion uses. EFT orders whose
   * proof is waiting for the merchant's review are never expired: the customer has done their part.
   *
   * @return how many orders were expired
   */
  @Transactional
  public int expireStaleUnpaidOrders(
      Instant now, java.time.Duration inAppTtl, java.time.Duration eftTtl, java.time.Duration cashTtl) {
    java.time.Duration shortest = inAppTtl;
    if (eftTtl.compareTo(shortest) < 0) shortest = eftTtl;
    if (cashTtl.compareTo(shortest) < 0) shortest = cashTtl;
    int expired = 0;
    for (OrderEntity o : orders.findUnpaidCreatedBefore(now.minus(shortest))) {
      java.time.Duration ttl;
      if (o.paymentMethod == OrderEntity.PaymentMethod.eft) {
        if (o.paymentVerificationState != OrderEntity.PaymentVerificationState.awaiting_proof) continue;
        ttl = eftTtl;
      } else if (o.paymentMethod == OrderEntity.PaymentMethod.cash_store) {
        ttl = cashTtl;
      } else {
        ttl = inAppTtl;
      }
      if (o.createdAt.isAfter(now.minus(ttl))) continue;
      o.status = OrderEntity.OrderStatus.cancelled;
      o.cancelledAt = now;
      o.expiredAt = now;
      releasePromotion(o);
      orders.save(o);
      expired++;
    }
    return expired;
  }

  private static boolean validCoordinate(Double lat, Double lng) {
    return lat != null
        && lng != null
        && Double.isFinite(lat)
        && Double.isFinite(lng)
        && Math.abs(lat) <= 90
        && Math.abs(lng) <= 180;
  }

  /**
   * Removes the order and its lines from the database. Allowed only while the order is not paid (pending payment or
   * cancelled). Paid orders must be retained for audit and stock history.
   */
  @Transactional
  public boolean deleteOrderPermanentlyIfUnpaid(UUID tenantId, UUID orderId) {
    if (orderId == null) return false;
    OrderEntity o = lockOrder(tenantId, orderId);
    if (o == null) return false;
    if (o.status == OrderEntity.OrderStatus.paid) return false;

    if (o.status == OrderEntity.OrderStatus.pending_payment && o.cancelledAt == null) {
      releasePromotion(o); // cancelled orders already gave theirs back
    }
    orderItems.deleteByTenantIdAndOrderId(tenantId, orderId);
    orders.delete(o);
    return true;
  }

  private static String safeTrim(String s) {
    return s == null ? "" : s.trim();
  }

  private static String generateCashPaymentCode() {
    int n = 100_000 + new SecureRandom().nextInt(900_000);
    return String.valueOf(n);
  }

  /**
   * Loads an order with a row lock for any change to its payment state, so a staff confirm/cancel and a payment
   * notification for the same order run one after the other (the webhook locks the same row).
   */
  private OrderEntity lockOrder(UUID tenantId, UUID orderId) {
    if (orderId == null) return null;
    return orders.lockById(orderId).filter(o -> o.tenantId.equals(tenantId)).orElse(null);
  }

  /** Customers are shown the short order reference; older payments may quote the full id. */
  private static boolean eftReferenceMatchesOrder(String declared, UUID orderId) {
    return RefCodes.bankReferenceMentions(declared, orderId);
  }

  private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
    double R = 6371.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }
}

