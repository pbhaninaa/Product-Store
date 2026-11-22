package com.productstore.platform.controllers;

import static com.productstore.platform.constants.MerchantPermission.*;

import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.services.CheckoutService;
import com.productstore.platform.services.EmployeeService;
import com.productstore.platform.services.MerchantAccessService;
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
  private final MerchantAccessService access;
  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final CheckoutService checkoutService;
  private final EmployeeService employeeService;

  public AdminOrdersController(
      TenantAccessService tenantAccess,
      MerchantAccessService access,
      OrderRepository orders,
      OrderItemRepository orderItems,
      CheckoutService checkoutService,
      EmployeeService employeeService) {
    this.tenantAccess = tenantAccess;
    this.access = access;
    this.orders = orders;
    this.orderItems = orderItems;
    this.checkoutService = checkoutService;
    this.employeeService = employeeService;
  }

  @GetMapping
  public Map<String, Object> list(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    // Read-only list is shared by the Orders, Payments and Insights pages.
    access.requireAny(principal, tenant.id(), ORDERS, PAYMENTS, INSIGHTS);
    return checkoutService.listAdminOrders(tenant.id());
  }

  @GetMapping("/{orderId}/items")
  public Map<String, Object> items(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireAny(principal, tenant.id(), ORDERS, PAYMENTS, INSIGHTS);

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
    var caller = access.requireAll(principal, tenant.id(), ORDERS, CONFIRM_PAYMENTS);
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
    if (completedByEmployeeId != null) {
      // Crediting work counts towards payroll, so choosing who gets it needs payroll access.
      MerchantAccessService.check(caller, TEAM_PAYROLL);
      employeeService.requireActiveEmployee(tenant.id(), completedByEmployeeId);
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
    access.requireAll(principal, tenant.id(), ORDERS);
    boolean ok = checkoutService.cancelUnpaid(tenant.id(), orderId);
    return Map.of("ok", ok);
  }

  @DeleteMapping("/{orderId}")
  public Map<String, Object> deletePermanently(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireAll(principal, tenant.id(), ORDERS, DELETE_RECORDS);
    boolean ok = checkoutService.deleteOrderPermanentlyIfUnpaid(tenant.id(), orderId);
    return ok ? Map.of("ok", true) : Map.of("ok", false, "reason", "not_deletable");
  }

  public record FulfillmentBody(String status) {}

  @PostMapping("/{orderId}/fulfillment")
  public Map<String, Object> fulfillment(
      @PathVariable String merchantSlug,
      @PathVariable UUID orderId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody FulfillmentBody body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireAll(principal, tenant.id(), ORDERS);
    String status = body == null ? "" : body.status();
    return checkoutService.updateFulfillment(tenant.id(), orderId, status);
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

}

