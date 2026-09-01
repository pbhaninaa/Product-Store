package com.productstore.platform.controllers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.models.PayFastCheckoutResponse;
import com.productstore.platform.repositories.OrderItemRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.services.PayFastPaymentService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;
import com.productstore.platform.util.InAppPaymentMethods;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients/me")
public class ClientHistoryController {
  private final OrderRepository orders;
  private final OrderItemRepository orderItems;
  private final SalonBookingRepository bookings;
  private final SalonServiceRepository salonServices;
  private final TenantRepository tenants;
  private final PayFastPaymentService payFastPaymentService;

  public ClientHistoryController(
      OrderRepository orders,
      OrderItemRepository orderItems,
      SalonBookingRepository bookings,
      SalonServiceRepository salonServices,
      TenantRepository tenants,
      PayFastPaymentService payFastPaymentService) {
    this.orders = orders;
    this.orderItems = orderItems;
    this.bookings = bookings;
    this.salonServices = salonServices;
    this.tenants = tenants;
    this.payFastPaymentService = payFastPaymentService;
  }

  @GetMapping("/orders")
  public List<Map<String, Object>> orders(@AuthenticationPrincipal ApiUserPrincipal principal) {
    requireClient(principal);
    List<Map<String, Object>> out = new ArrayList<>();
    for (OrderEntity o : orders.findByClientUserIdOrderByCreatedAtDesc(principal.userId())) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", o.id.toString());
      m.put("tenantId", o.tenantId.toString());
      String slug = tenants.findById(o.tenantId).map(t -> t.slug).orElse("");
      m.put("merchantSlug", slug);
      m.put("status", o.status != null ? o.status.name() : "");
      m.put("fulfillmentStatus", o.fulfillmentStatus != null ? o.fulfillmentStatus.name() : "");
      m.put("paymentMethod", o.paymentMethod != null ? o.paymentMethod.name() : "");
      m.put("peachPaymentMethod", o.peachPaymentMethod);
      m.put("totalZar", o.totalZar);
      m.put("createdAt", o.createdAt != null ? o.createdAt.toString() : null);
      m.put("customerEmail", o.customerEmail);
      m.put("itemCount", orderItems.countByOrderId(o.id));
      boolean pendingInApp =
          o.status == OrderEntity.OrderStatus.pending_payment
              && o.cancelledAt == null
              && InAppPaymentMethods.isInApp(o.paymentMethod);
      m.put("canPayNow", pendingInApp);
      out.add(m);
    }
    return out;
  }

  @GetMapping("/bookings")
  public List<Map<String, Object>> bookings(@AuthenticationPrincipal ApiUserPrincipal principal) {
    requireClient(principal);
    List<Map<String, Object>> out = new ArrayList<>();
    for (SalonBookingEntity b : bookings.findByClientUserIdOrderByStartAtDesc(principal.userId())) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", b.id.toString());
      m.put("tenantId", b.tenantId.toString());
      String slug = tenants.findById(b.tenantId).map(t -> t.slug).orElse("");
      m.put("merchantSlug", slug);
      m.put("status", b.status != null ? b.status.name() : "");
      m.put("paymentMethod", b.clientPaymentMethod != null ? b.clientPaymentMethod.name() : "");
      m.put("startAt", b.startAt != null ? b.startAt.toString() : null);
      m.put("customerEmail", b.customerEmail);
      m.put("serviceId", b.serviceId != null ? b.serviceId.toString() : null);
      salonServices
          .findByIdAndTenantId(b.serviceId, b.tenantId)
          .ifPresent(s -> m.put("serviceName", s.name));
      boolean pendingInApp =
          b.status == SalonBookingEntity.Status.pending && InAppPaymentMethods.isInApp(b.clientPaymentMethod);
      m.put("canPayNow", pendingInApp);
      out.add(m);
    }
    return out;
  }

  @PostMapping("/orders/{orderId}/payfast-checkout")
  public Map<String, Object> payOrder(
      @AuthenticationPrincipal ApiUserPrincipal principal, @PathVariable UUID orderId) {
    requireClient(principal);
    OrderEntity o =
        orders.findByClientUserIdOrderByCreatedAtDesc(principal.userId()).stream()
            .filter(row -> orderId.equals(row.id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("not_found"));
    String slug = tenants.findById(o.tenantId).map(t -> t.slug).orElseThrow(() -> new IllegalArgumentException("tenant_missing"));
    return toCheckoutMap(payFastPaymentService.initiateOrderCheckout(o.tenantId, o.id, slug));
  }

  @PostMapping("/bookings/{bookingId}/payfast-checkout")
  public Map<String, Object> payBooking(
      @AuthenticationPrincipal ApiUserPrincipal principal, @PathVariable UUID bookingId) {
    requireClient(principal);
    SalonBookingEntity b =
        bookings.findByClientUserIdOrderByStartAtDesc(principal.userId()).stream()
            .filter(row -> bookingId.equals(row.id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("not_found"));
    String slug =
        tenants.findById(b.tenantId).map(t -> t.slug).orElseThrow(() -> new IllegalArgumentException("tenant_missing"));
    return toCheckoutMap(payFastPaymentService.initiateBookingCheckout(b.tenantId, b.id, slug));
  }

  private static Map<String, Object> toCheckoutMap(PayFastCheckoutResponse session) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("paymentId", session.paymentId());
    m.put("processUrl", session.processUrl());
    m.put("fields", session.fields());
    return m;
  }

  private static void requireClient(ApiUserPrincipal principal) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    boolean ok = principal.roles().stream().anyMatch(r -> r == Role.CLIENT);
    if (!ok) throw new IllegalArgumentException("forbidden");
  }
}
