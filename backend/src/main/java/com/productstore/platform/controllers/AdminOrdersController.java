package com.productstore.platform.controllers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.services.CheckoutService;
import com.productstore.platform.services.TenantAccessService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/orders")
public class AdminOrdersController {
  private final TenantAccessService tenantAccess;
  private final MembershipRepository memberships;
  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final CheckoutService checkoutService;

  public AdminOrdersController(
      TenantAccessService tenantAccess,
      MembershipRepository memberships,
      OrderRepository orders,
      OrderItemRepository orderItems,
      CheckoutService checkoutService) {
    this.tenantAccess = tenantAccess;
    this.memberships = memberships;
    this.orders = orders;
    this.orderItems = orderItems;
    this.checkoutService = checkoutService;
  }

  @GetMapping
  public Map<String, Object> list(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());

    var rows = orders.findAllByTenant(tenant.id());
    var payload =
        rows.stream()
            .map(
                o -> {
                  Map<String, Object> m = new LinkedHashMap<>();
                  m.put("id", o.id.toString());
                  m.put("createdAt", o.createdAt.toString());
                  m.put("customerName", o.customerName);
                  m.put("customerEmail", o.customerEmail);
                  m.put("customerPhone", nz(o.customerPhone));
                  m.put("deliveryType", o.deliveryType.name());
                  m.put("deliveryAddress", nz(o.deliveryAddress));
                  m.put("paymentMethod", o.paymentMethod.name());
                  m.put("paymentVerificationState", o.paymentVerificationState.name());
                  m.put("status", o.status.name());
                  m.put("subtotalZar", o.subtotalZar.toPlainString());
                  m.put("deliveryFeeZar", o.deliveryFeeZar.toPlainString());
                  m.put("totalZar", o.totalZar.toPlainString());
                  if (o.cashPaymentCode != null && !o.cashPaymentCode.isBlank()) {
                    m.put("cashPaymentCode", o.cashPaymentCode);
                  }
                  return m;
                })
            .toList();
    return Map.of("orders", payload);
  }

  @GetMapping("/{orderId}/items")
  public Map<String, Object> items(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());

    var lines = orderItems.findAllByTenantAndOrderId(tenant.id(), orderId);
    var payload =
        lines.stream()
            .map(
                l ->
                    Map.<String, Object>of(
                        "productId", l.productId.toString(),
                        "quantity", l.quantity,
                        "unitPriceZar", l.unitPriceZar.toPlainString(),
                        "lineTotalZar", l.lineTotalZar.toPlainString()))
            .toList();
    return Map.of("items", payload);
  }

  @PostMapping("/{orderId}/confirm-payment")
  public Map<String, Object> confirm(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody(required = false) Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    String cash = "";
    if (body != null && body.get("cashCode") != null) {
      cash = String.valueOf(body.get("cashCode"));
    }
    boolean ok = checkoutService.confirmPayment(tenant.id(), orderId, cash);
    return Map.of("ok", ok);
  }

  @PostMapping("/{orderId}/cancel")
  public Map<String, Object> cancel(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    boolean ok = checkoutService.cancelUnpaid(tenant.id(), orderId);
    return Map.of("ok", ok);
  }

  @DeleteMapping("/{orderId}")
  public Map<String, Object> deletePermanently(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());
    boolean ok = checkoutService.deleteOrderPermanentlyIfUnpaid(tenant.id(), orderId);
    return ok ? Map.of("ok", true) : Map.of("ok", false, "reason", "not_deletable");
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private void requireMerchantAccess(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    List<Role> roles = List.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF);
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(principal.userId(), tenantId, roles)
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }
}

