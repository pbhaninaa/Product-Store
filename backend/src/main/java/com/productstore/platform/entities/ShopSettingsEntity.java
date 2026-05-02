package com.productstore.platform.entities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

  @Column(name = "store_hero_url", nullable = false, columnDefinition = "text")
  public String storeHeroUrl;

  /** {@code normal_store}, {@code salon_and_store}, or {@code salon_only} (legacy {@code salon} normalized on read). */
  @Column(name = "shop_type", length = 48)
  public String shopType;

  /** JSON array of per-day windows, e.g. {@code [{"dayOfWeek":1,"start":"09:00","end":"17:00"}]} — optional. */
  @Column(name = "opening_hours_json", columnDefinition = "text")
  public String openingHoursJson;

  /** When false, checkout and salon booking must not offer EFT. */
  @Column(name = "accept_customer_eft", nullable = false)
  public Boolean acceptCustomerEft;

  /** When false, checkout and salon booking must not offer pay-in-store cash. */
  @Column(name = "accept_customer_cash", nullable = false)
  public Boolean acceptCustomerCash;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}

