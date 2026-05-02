package com.productstore.platform.controllers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.services.CheckoutService;
import com.productstore.platform.services.TenantAccessService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
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

  public PublicCheckoutController(TenantAccessService tenantAccess, CheckoutService checkoutService) {
    this.tenantAccess = tenantAccess;
    this.checkoutService = checkoutService;
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
      @NotBlank String paymentMethod,
      @NotNull List<CreateOrderLine> items) {}

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createOrder(
      @PathVariable String merchantSlug, @Valid @RequestBody CreateOrderRequest req) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);

    var cmd =
        new CheckoutService.CreateOrderCommand(
            req.customerName(),
            req.customerEmail(),
            req.customerPhone(),
            OrderEntity.DeliveryType.valueOf(req.deliveryType()),
            req.deliveryAddress(),
            req.deliveryLat(),
            req.deliveryLng(),
            OrderEntity.PaymentMethod.valueOf(req.paymentMethod()),
            req.items().stream()
                .map(l -> new CheckoutService.CreateOrderLine(l.product_id(), l.quantity()))
                .toList());

    CheckoutService.CreateOrderResult created = checkoutService.createOrder(tenant.id(), cmd);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("orderId", created.orderId().toString());
    out.put("needsEftProof", created.needsEftProof());
    if (created.cashPaymentCode() != null && !created.cashPaymentCode().isBlank()) {
      out.put("cashPaymentCode", created.cashPaymentCode());
      out.put("needsCashPaymentCode", Boolean.TRUE);
    }
    return out;
  }

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
}

