package com.productstore.platform.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderEntity {
  public enum DeliveryType {
    pickup,
    delivery
  }

  public enum PaymentMethod {
    eft,
    cash_store
  }

  public enum OrderStatus {
    pending_payment,
    paid,
    cancelled
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

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  public OrderStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_verification_state", nullable = false, length = 32)
  public PaymentVerificationState paymentVerificationState = PaymentVerificationState.not_applicable;

  @Column(name = "payment_proof_path", columnDefinition = "text")
  public String paymentProofPath;

  @Column(name = "payment_reference_declared", length = 512)
  public String paymentReferenceDeclared;

  /** Shown to the customer for pay-in-store; staff enters it in admin to mark the order paid. */
  @Column(name = "cash_payment_code", length = 16)
  public String cashPaymentCode;

  @Column(name = "payment_confirmed_at")
  public Instant paymentConfirmedAt;

  @Column(name = "cancelled_at")
  public Instant cancelledAt;

  @Column(name = "subtotal_zar", nullable = false)
  public BigDecimal subtotalZar;

  @Column(name = "total_zar", nullable = false)
  public BigDecimal totalZar;
}

