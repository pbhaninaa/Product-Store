package com.productstore.platform.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(
    name = "orders",
    indexes = {
      @Index(name = "idx_orders_online_merchant_tx", columnList = "online_payment_reference"),
      @Index(name = "idx_orders_online_checkout", columnList = "online_payment_provider_id")
    })
public class OrderEntity {
  public enum DeliveryType {
    pickup,
    delivery
  }

  public enum PaymentMethod {
    /** Manual bank transfer + proof upload. */
    eft,
    cash_store,
    /** Online hosted checkout (card or Instant EFT) through the platform payment provider. */
    online
  }

  public enum OrderStatus {
    pending_payment,
    paid,
    cancelled
  }

  /**
   * Wheel Hub-style job progress after payment. {@code PAID} stays on {@link OrderStatus}; merchants then move
   * processing → ready → completed. Null on unpaid rows; paid rows with a null column are treated as processing.
   */
  public enum FulfillmentStatus {
    processing,
    ready,
    completed
  }

  /**
   * EFT: awaiting customer proof → auto match order ref → paid, or manual_pending for merchant review (see
   * {@link com.productstore.platform.services.CheckoutService}).
   */
  public enum PaymentVerificationState {
    not_applicable,
    awaiting_proof,
    auto_verified,
    manual_pending,
    manual_rejected
  }

  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false)
  public UUID tenantId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "customer_name", nullable = false)
  public String customerName;

  @Column(name = "customer_email", nullable = false)
  public String customerEmail;

  @Column(name = "customer_phone")
  public String customerPhone;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_type", nullable = false)
  public DeliveryType deliveryType;

  @Column(name = "delivery_address")
  public String deliveryAddress;

  @Column(name = "delivery_lat")
  public Double deliveryLat;

  @Column(name = "delivery_lng")
  public Double deliveryLng;

  @Column(name = "delivery_fee_zar", nullable = false)
  public BigDecimal deliveryFeeZar;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_method", nullable = false)
  public PaymentMethod paymentMethod;

  /** Selected online payment rail; null for cash and manual EFT rows. */
  @Enumerated(EnumType.STRING)
  @Column(name = "online_payment_channel", length = 16)
  public OnlinePaymentChannel onlinePaymentChannel;

  /** Logged-in CLIENT account that placed this order, when known. */
  @Column(name = "client_user_id")
  public UUID clientUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  public OrderStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "fulfillment_status", length = 24)
  public FulfillmentStatus fulfillmentStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_verification_state", nullable = false, length = 32)
  public PaymentVerificationState paymentVerificationState = PaymentVerificationState.not_applicable;

  @Column(name = "payment_proof_path", columnDefinition = "text")
  public String paymentProofPath;
  @Lob

  @Column(name = "payment_proof_data", columnDefinition = "LONGBLOB")
  public byte[] paymentProofData;

  @Column(name = "payment_proof_content_type", length = 100)
  public String paymentProofContentType;

  @Column(name = "payment_reference_declared", length = 512)
  public String paymentReferenceDeclared;

  /** The payment provider's transaction id once an online payment is confirmed. */
  @Column(name = "online_payment_provider_id", length = 128)
  public String onlinePaymentProviderId;

  /** Our reference for the latest online checkout attempt; matches provider notifications. */
  @Column(name = "online_payment_reference", length = 64)
  public String onlinePaymentReference;

  /**
   * Handover code shown to the customer for every payment method. Staff enter it to hand the order over
   * (collection or delivery); for pay-in-store it also marks the order paid. Column keeps its original name.
   */
  @Column(name = "cash_payment_code", length = 16)
  public String handoverCode;

  @Column(name = "payment_confirmed_at")
  public Instant paymentConfirmedAt;

  @Column(name = "cancelled_at")
  public Instant cancelledAt;

  @Column(name = "subtotal_zar", nullable = false)
  public BigDecimal subtotalZar;

  @Column(name = "total_zar", nullable = false)
  public BigDecimal totalZar;

  /** Team employee (employees.id) credited for payroll when this order is paid. */
  @Column(name = "completed_by_employee_id")
  public UUID completedByEmployeeId;

  /** When the order became paid (payroll / attribution timestamp). */
  @Column(name = "completed_at")
  public Instant completedAt;

  /** Promotion whose use this order holds; released if the order is cancelled or expires unpaid. */
  @Column(name = "promotion_id")
  public UUID promotionId;

  /** Set when the order was cancelled automatically for staying unpaid too long. */
  @Column(name = "expired_at")
  public Instant expiredAt;

  /** Straight-line km from the store to the customer's pin, when the fee was charged per km. */
  @Column(name = "delivery_distance_km")
  public Double deliveryDistanceKm;

  public static FulfillmentStatus effectiveFulfillment(OrderEntity o) {
    if (o == null || o.status == OrderStatus.cancelled) return null;
    if (o.status != OrderStatus.paid) return null;
    return o.fulfillmentStatus == null ? FulfillmentStatus.processing : o.fulfillmentStatus;
  }
}

