package com.productstore.platform.controllers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.OnlinePaymentChannel;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.services.CheckoutService;
import com.productstore.platform.services.PaystackPaymentService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;
import com.productstore.platform.util.OnlinePaymentMethods;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/m/{merchantSlug}/checkout")
public class PublicCheckoutController {
  private final TenantAccessService tenantAccess;
  private final CheckoutService checkoutService;
  private final PaystackPaymentService paystack;

  public PublicCheckoutController(
      TenantAccessService tenantAccess,
      CheckoutService checkoutService,
      PaystackPaymentService paystack) {
    this.tenantAccess = tenantAccess;
    this.checkoutService = checkoutService;
    this.paystack = paystack;
  }

  public record CreateOrderLine(@NotNull UUID product_id, @NotNull Integer quantity) {}

  public record CreateOrderRequest(
      @NotBlank String customerName,
      @Email @NotBlank String customerEmail,
      @NotBlank String customerPhone,
      @NotBlank String deliveryType,
      String deliveryAddress,
      Double deliveryLat,
      Double deliveryLng,
      /** {@code cash_store} or {@code online}. {@code eft} (Manual EFT) is refused with {@code eft_not_accepted}. */
      @NotBlank String paymentMethod,
      /** Required for {@code online}: {@code CARD} or {@code EFT}. */
      String onlinePaymentChannel,
      String promoId,
      @NotNull List<CreateOrderLine> items) {}

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createOrder(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @Valid @RequestBody CreateOrderRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);

    OrderEntity.PaymentMethod pm = OnlinePaymentMethods.parseOrderMethod(req.paymentMethod());
    if (pm == null) {
      throw new IllegalArgumentException("invalid_payment_method");
    }
    boolean channelGiven = req.onlinePaymentChannel() != null && !req.onlinePaymentChannel().isBlank();
    if (!OnlinePaymentMethods.isOnline(pm) && channelGiven) {
      throw new IllegalArgumentException("online_payment_channel_not_applicable");
    }
    OnlinePaymentChannel channel =
        OnlinePaymentMethods.isOnline(pm) ? OnlinePaymentChannel.fromRequest(req.onlinePaymentChannel()) : null;

    UUID promoId = null;
    if (req.promoId() != null && !req.promoId().isBlank()) {
      try {
        promoId = UUID.fromString(req.promoId().trim());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("invalid_promo");
      }
    }
    UUID clientUserId = null;
    if (principal != null && principal.roles().stream().anyMatch(r -> r == Role.CLIENT)) {
      clientUserId = principal.userId();
    }

    var cmd =
        new CheckoutService.CreateOrderCommand(
            req.customerName(),
            req.customerEmail(),
            req.customerPhone(),
            OrderEntity.DeliveryType.valueOf(req.deliveryType()),
            req.deliveryAddress(),
            req.deliveryLat(),
            req.deliveryLng(),
            pm,
            channel,
            req.items().stream()
                .map(l -> new CheckoutService.CreateOrderLine(l.product_id(), l.quantity()))
                .toList(),
            promoId,
            clientUserId);

    CheckoutService.CreateOrderResult created = checkoutService.createOrder(tenant.id(), cmd);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("orderId", created.orderId().toString());
    if (created.handoverCode() != null && !created.handoverCode().isBlank()) {
      out.put("handoverCode", created.handoverCode());
      if (pm == OrderEntity.PaymentMethod.cash_store) {
        // Older clients only know the cash code; it is the same handover code.
        out.put("cashPaymentCode", created.handoverCode());
        out.put("needsCashPaymentCode", Boolean.TRUE);
      }
    }
    if (OnlinePaymentMethods.isOnline(pm)) {
      paystack.startNewOrderCheckout(tenant.id(), created.orderId(), merchantSlug).addTo(out);
    }
    return out;
  }

  /** Return page: payment state of an order, confirming a still pending online payment with Paystack. */
  @GetMapping("/orders/{orderId}/payment-status")
  public Map<String, Object> orderPaymentStatus(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @RequestParam(value = "customerEmail", required = false) String customerEmail,
      @RequestParam(value = "reference", required = false) String reference) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    return paystack.orderStatus(tenant.id(), orderId, customerEmail, reference);
  }

  /** Legacy only: bank-transfer proof for an EFT order placed before Manual EFT was removed. */
  @PostMapping(path = "/orders/{orderId}/eft-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> submitOrderEftProof(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @RequestParam("customerEmail") String customerEmail,
      @RequestParam("bankReference") String bankReference,
      @RequestParam("proof") MultipartFile proof)
      throws Exception {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    return checkoutService.submitOrderEftProof(tenant.id(), orderId, customerEmail, bankReference, proof);
  }

  @GetMapping("/orders/{orderId}")
  public Map<String, Object> lookupOrder(
      @PathVariable String merchantSlug,
      @PathVariable String orderId,
      @RequestParam("customerEmail") String customerEmail) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    return checkoutService.lookupPublic(tenant.id(), orderId, customerEmail);
  }
}
