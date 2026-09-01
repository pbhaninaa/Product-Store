package com.productstore.platform.services;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.OrderReviewEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonBookingReviewEntity;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.OrderReviewRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonBookingReviewRepository;
import com.productstore.platform.util.Emails;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
  private final OrderRepository orders;
  private final SalonBookingRepository bookings;
  private final OrderReviewRepository orderReviews;
  private final SalonBookingReviewRepository bookingReviews;

  public ReviewService(
      OrderRepository orders,
      SalonBookingRepository bookings,
      OrderReviewRepository orderReviews,
      SalonBookingReviewRepository bookingReviews) {
    this.orders = orders;
    this.bookings = bookings;
    this.orderReviews = orderReviews;
    this.bookingReviews = bookingReviews;
  }

  public Map<String, Object> summary(UUID tenantId) {
    long orderCount = orderReviews.countByTenantId(tenantId);
    long bookingCount = bookingReviews.countByTenantId(tenantId);
    long total = orderCount + bookingCount;
    double orderAvg = orderCount == 0 ? 0 : nz(orderReviews.averageRatingByTenant(tenantId));
    double bookingAvg = bookingCount == 0 ? 0 : nz(bookingReviews.averageRatingByTenant(tenantId));
    double avg = 0;
    if (total > 0) {
      avg = ((orderAvg * orderCount) + (bookingAvg * bookingCount)) / total;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("averageRating", Math.round(avg * 10.0) / 10.0);
    out.put("reviewCount", total);
    return out;
  }

  @Transactional
  public Map<String, Object> submitOrderReview(
      UUID tenantId, UUID orderId, String customerEmail, int rating, String comment) {
    if (orderId == null) throw new IllegalArgumentException("invalid_order");
    String email = normalizeEmail(customerEmail);
    assertRating(rating);
    OrderEntity o = orders.findOneByTenantAndId(tenantId, orderId);
    if (o == null) throw new IllegalArgumentException("not_found");
    if (!email.equalsIgnoreCase(trim(o.customerEmail))) throw new IllegalArgumentException("email_mismatch");
    if (OrderEntity.effectiveFulfillment(o) != OrderEntity.FulfillmentStatus.completed) {
      throw new IllegalArgumentException("not_completed");
    }
    if (orderReviews.existsByOrderId(orderId)) throw new IllegalArgumentException("already_rated");

    OrderReviewEntity r = new OrderReviewEntity();
    r.id = UUID.randomUUID();
    r.tenantId = tenantId;
    r.orderId = orderId;
    r.customerEmail = email;
    r.rating = rating;
    r.comment = clipComment(comment);
    r.createdAt = Instant.now();
    orderReviews.save(r);
    return Map.of("ok", true, "id", r.id.toString(), "rating", r.rating);
  }

  @Transactional
  public Map<String, Object> submitBookingReview(
      UUID tenantId, UUID bookingId, String customerEmail, int rating, String comment) {
    if (bookingId == null) throw new IllegalArgumentException("invalid_booking");
    String email = normalizeEmail(customerEmail);
    assertRating(rating);
    SalonBookingEntity b = bookings.findOneByTenantAndId(tenantId, bookingId);
    if (b == null) throw new IllegalArgumentException("not_found");
    if (!email.equalsIgnoreCase(trim(b.customerEmail))) throw new IllegalArgumentException("email_mismatch");
    if (b.status != SalonBookingEntity.Status.completed) {
      throw new IllegalArgumentException("not_completed");
    }
    if (bookingReviews.existsByBookingId(bookingId)) throw new IllegalArgumentException("already_rated");

    SalonBookingReviewEntity r = new SalonBookingReviewEntity();
    r.id = UUID.randomUUID();
    r.tenantId = tenantId;
    r.bookingId = bookingId;
    r.customerEmail = email;
    r.rating = rating;
    r.comment = clipComment(comment);
    r.createdAt = Instant.now();
    bookingReviews.save(r);
    return Map.of("ok", true, "id", r.id.toString(), "rating", r.rating);
  }

  public boolean orderAlreadyRated(UUID orderId) {
    return orderId != null && orderReviews.existsByOrderId(orderId);
  }

  public boolean bookingAlreadyRated(UUID bookingId) {
    return bookingId != null && bookingReviews.existsByBookingId(bookingId);
  }

  private static void assertRating(int rating) {
    if (rating < 1 || rating > 5) throw new IllegalArgumentException("invalid_rating");
  }

  private static String normalizeEmail(String raw) {
    String email = trim(raw).toLowerCase();
    if (!Emails.isValid(email)) throw new IllegalArgumentException("invalid_email");
    return email;
  }

  private static String clipComment(String comment) {
    String c = trim(comment);
    return c.length() > 2000 ? c.substring(0, 2000) : c;
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }

  private static double nz(Double d) {
    return d == null ? 0 : d;
  }
}
