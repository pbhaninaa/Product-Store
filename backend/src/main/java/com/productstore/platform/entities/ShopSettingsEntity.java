package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "shop_settings")
public class ShopSettingsEntity {
  @Id
  public UUID id;

  @Column(name = "tenant_id", nullable = false, unique = true)
  public UUID tenantId;

  @Column(name = "delivery_fee_mode", nullable = false)
  public String deliveryFeeMode;

  @Column(name = "delivery_fee_flat_zar", nullable = false)
  public java.math.BigDecimal deliveryFeeFlatZar;

  @Column(name = "delivery_fee_per_km_zar", nullable = false)
  public java.math.BigDecimal deliveryFeePerKmZar;

  @Column(name = "store_lat")
  public Double storeLat;

  @Column(name = "store_lng")
  public Double storeLng;

  /** Legacy Manual EFT instructions (removed 2026-11-23). Column kept for history; never sent to clients. */
  @JsonIgnore
  @Column(name = "eft_bank_instructions", nullable = false)
  public String eftBankInstructions;

  @Column(name = "bank_name", nullable = false)
  public String bankName;

  @Column(name = "bank_account_holder", nullable = false)
  public String bankAccountHolder;

  @Column(name = "bank_account_number", nullable = false)
  public String bankAccountNumber;

  @Column(name = "bank_branch_code", nullable = false)
  public String bankBranchCode;

  /** Paystack bank code (South Africa), used to resolve and pay out. */
  @Column(name = "bank_code")
  public String bankCode;

  /** Name Paystack returned for the saved account; required before a payout can be sent. */
  @Column(name = "payout_account_verified_name")
  public String payoutAccountVerifiedName;

  /**
   * Account type: "personal" or "business". Required by Paystack's South African account validation
   * endpoint. Set during account verification and stored for creating transfer recipients.
   */
  @Column(name = "payout_account_type")
  public String payoutAccountType;

  @JsonIgnore
  @Column(name = "payout_paystack_recipient_code")
  public String payoutPaystackRecipientCode;

  @Column(name = "store_name", nullable = false)
  public String storeName;

  @Column(name = "contact_email", nullable = false)
  public String contactEmail;

  @Column(name = "contact_phone", nullable = false)
  public String contactPhone;

  @Column(name = "contact_address", nullable = false, columnDefinition = "text")
  public String contactAddress;

  @Column(name = "contact_notes", nullable = false, columnDefinition = "text")
  public String contactNotes;

  @Column(name = "store_logo_url", nullable = false, columnDefinition = "text")
  public String storeLogoUrl;
  @Lob

  @Column(name = "store_logo_data", columnDefinition = "LONGBLOB")
  public byte[] storeLogoData;

  @Column(name = "store_logo_content_type", length = 100)
  public String storeLogoContentType;

  @Column(name = "store_hero_url", nullable = false, columnDefinition = "text")
  public String storeHeroUrl;
  @Lob

  @Column(name = "store_hero_data", columnDefinition = "LONGBLOB")
  public byte[] storeHeroData;

  @Column(name = "store_hero_content_type", length = 100)
  public String storeHeroContentType;

  /** {@code normal_store}, {@code salon_and_store}, or {@code salon_only} (legacy {@code salon} normalized on read). */
  @Column(name = "shop_type", length = 48)
  public String shopType;

  /** JSON array of per-day windows, e.g. {@code [{"dayOfWeek":1,"start":"09:00","end":"17:00"}]} — optional. */
  @Column(name = "opening_hours_json", columnDefinition = "text")
  public String openingHoursJson;

  /** When false, checkout and salon booking must not offer online payment (card or Instant EFT). */
  @Column(name = "accept_customer_online", nullable = false)
  public Boolean acceptCustomerOnline;

  /**
   * Legacy Manual EFT toggle. Manual EFT was removed on 2026-11-23 and is never offered whatever this says (see
   * {@code CustomerPaymentPolicy#acceptEft}). Column kept for history; never sent to clients.
   */
  @JsonIgnore
  @Column(name = "accept_customer_eft", nullable = false)
  public Boolean acceptCustomerEft;

  /** When false, checkout and salon booking must not offer pay-in-store cash. */
  @Column(name = "accept_customer_cash", nullable = false)
  public Boolean acceptCustomerCash;

  /**
   * Booking fee for pay-in-store salon bookings: a whole percent of the booking price, 0 to 50, paid online when
   * booking. Null means 0 (no fee).
   */
  @Column(name = "booking_fee_percent")
  public Integer bookingFeePercent;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}

