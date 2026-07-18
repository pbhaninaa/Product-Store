package com.productstore.platform.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.productstore.platform.entities.OrderItemEntity;
import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.services.CheckoutService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

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
  private final ProductRepository products;
  private final CheckoutService checkoutService;

  public AdminOrdersController(
      TenantAccessService tenantAccess,
      MembershipRepository memberships,
      OrderRepository orders,
      OrderItemRepository orderItems,
      ProductRepository products,
      CheckoutService checkoutService) {
    this.tenantAccess = tenantAccess;
    this.memberships = memberships;
    this.orders = orders;
    this.orderItems = orderItems;
    this.products = products;
    this.checkoutService = checkoutService;
  }

  @GetMapping
  public Map<String, Object> list(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchantAccess(principal, tenant.id());

    var rows = orders.findAllByTenant(tenant.id());
    var allLines = orderItems.findAllByTenantId(tenant.id());
    Map<UUID, List<OrderItemEntity>> linesByOrder =
        allLines.stream().collect(Collectors.groupingBy(l -> l.orderId));
    Map<UUID, String> productNames = productNameMap(tenant.id(), allLines);

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
                  if (o.deliveryLat != null) {
                    m.put("deliveryLat", o.deliveryLat);
                  }
                  if (o.deliveryLng != null) {
                    m.put("deliveryLng", o.deliveryLng);
                  }
                  m.put("paymentMethod", o.paymentMethod.name());
                  m.put(
                      "peachPaymentMethod",
                      o.peachPaymentMethod == null ? "" : o.peachPaymentMethod.name());
                  m.put("paymentVerificationState", o.paymentVerificationState.name());
                  m.put("status", o.status.name());
                  m.put("subtotalZar", o.subtotalZar.toPlainString());
                  m.put("deliveryFeeZar", o.deliveryFeeZar.toPlainString());
                  m.put("totalZar", o.totalZar.toPlainString());
                  if (o.paymentConfirmedAt != null) {
                    m.put("paymentConfirmedAt", o.paymentConfirmedAt.toString());
                  }
                  if (o.cancelledAt != null) {
                    m.put("cancelledAt", o.cancelledAt.toString());
                  }
                  // Do not expose cashPaymentCode — staff must enter the code the customer shows.
                  if (o.completedByEmployeeId != null) {
                    m.put("completedByEmployeeId", o.completedByEmployeeId.toString());
                  }
                  if (o.completedAt != null) {
                    m.put("completedAt", o.completedAt.toString());
                  }
                  List<OrderItemEntity> lines =
                      linesByOrder.getOrDefault(o.id, List.of());
                  m.put("items", mapItems(lines, productNames));
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
    Map<UUID, String> productNames = productNameMap(tenant.id(), lines);
    return Map.of("items", mapItems(lines, productNames));
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
    UUID completedByEmployeeId = null;
    if (body != null) {
      if (body.get("cashCode") != null) {
        cash = String.valueOf(body.get("cashCode"));
      }
      if (body.get("completedByEmployeeId") != null
          && !String.valueOf(body.get("completedByEmployeeId")).isBlank()) {
        completedByEmployeeId = UUID.fromString(String.valueOf(body.get("completedByEmployeeId")));
      }
    }
    boolean ok = checkoutService.confirmPayment(tenant.id(), orderId, cash, completedByEmployeeId);
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

  private Map<UUID, String> productNameMap(UUID tenantId, List<OrderItemEntity> lines) {
    Map<UUID, String> names = new HashMap<>();
    if (lines == null || lines.isEmpty()) return names;
    List<UUID> ids =
        lines.stream().map(l -> l.productId).distinct().collect(Collectors.toList());
    if (!ids.isEmpty()) {
      for (ProductEntity p : products.findActiveByTenantAndIds(tenantId, ids)) {
        names.put(p.id, p.name);
      }
    }
    // Include archived products so historical invoices still show names.
    for (UUID id : ids) {
      if (names.containsKey(id)) continue;
      products.findByIdAndTenantId(id, tenantId).ifPresent(p -> names.put(p.id, p.name));
    }
    return names;
  }

  private static List<Map<String, Object>> mapItems(
      List<OrderItemEntity> lines, Map<UUID, String> productNames) {
    List<Map<String, Object>> out = new ArrayList<>(lines.size());
    for (OrderItemEntity l : lines) {
      Map<String, Object> im = new LinkedHashMap<>();
      im.put("id", l.id.toString());
      im.put("productId", l.productId.toString());
      im.put("productName", productNames.getOrDefault(l.productId, "Product"));
      im.put("quantity", l.quantity);
      im.put("unitPriceZar", l.unitPriceZar.toPlainString());
      im.put("lineTotalZar", l.lineTotalZar.toPlainString());
      out.add(im);
    }
    return out;
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
