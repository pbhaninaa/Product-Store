package com.productstore.platform.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.SalonStaffAvailabilityEntity;
import com.productstore.platform.entities.SalonStaffEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a demo merchant (default slug {@code demo}) on the {@code sit} profile only. Safe to run
 * repeatedly: only inserts missing tenant, user, membership, or shop-settings row.
 */
@Service
@Order(25)
public class DemoMerchantBootstrapService implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DemoMerchantBootstrapService.class);

  private final TenantRepository tenants;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final ShopSettingsRepository shopSettings;
  private final ProductRepository products;
  private final SalonServiceRepository salonServices;
  private final SalonStaffRepository salonStaff;
  private final SalonStaffAvailabilityRepository salonAvailability;
  private final PasswordHasher passwordHasher;
  private final MerchantSubscriptionService subscriptions;
  private final Environment environment;

  private final boolean enabled;
  private final String slug;
  private final String displayName;
  private final String ownerEmail;
  private final String ownerPassword;

  public DemoMerchantBootstrapService(
      TenantRepository tenants,
      UserRepository users,
      MembershipRepository memberships,
      ShopSettingsRepository shopSettings,
      ProductRepository products,
      SalonServiceRepository salonServices,
      SalonStaffRepository salonStaff,
      SalonStaffAvailabilityRepository salonAvailability,
      PasswordHasher passwordHasher,
      MerchantSubscriptionService subscriptions,
      Environment environment,
      @Value("${app.bootstrap.demoMerchant.enabled:false}") boolean enabled,
      @Value("${app.bootstrap.demoMerchant.slug:demo}") String slug,
      @Value("${app.bootstrap.demoMerchant.displayName:Demo Store}") String displayName,
      @Value("${app.bootstrap.demoMerchant.ownerEmail:}") String ownerEmail,
      @Value("${app.bootstrap.demoMerchant.ownerPassword:}") String ownerPassword) {
    this.tenants = tenants;
    this.users = users;
    this.memberships = memberships;
    this.shopSettings = shopSettings;
    this.products = products;
    this.salonServices = salonServices;
    this.salonStaff = salonStaff;
    this.salonAvailability = salonAvailability;
    this.passwordHasher = passwordHasher;
    this.subscriptions = subscriptions;
    this.environment = environment;
    this.enabled = enabled;
    this.slug = slug == null ? "" : slug.trim().toLowerCase();
    this.displayName = displayName == null ? "Demo Store" : displayName.trim();
    this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim().toLowerCase();
    this.ownerPassword = ownerPassword == null ? "" : ownerPassword;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled || slug.isBlank() || ownerEmail.isBlank() || ownerPassword.isBlank()) {
      return;
    }
    if (!isSitProfile()) {
      log.warn("Skipping demo merchant bootstrap; sit profile is not active");
      return;
    }
    ensureDemoMerchant();
  }

  private boolean isSitProfile() {
    for (String p : environment.getActiveProfiles()) {
      if ("sit".equalsIgnoreCase(p)) return true;
    }
    return false;
  }

  @Transactional
  protected void ensureDemoMerchant() {
    boolean createdTenant = false;
    TenantEntity tenant =
        tenants
            .findBySlug(slug)
            .orElse(null);
    if (tenant == null) {
      TenantEntity t = new TenantEntity();
      t.id = UUID.randomUUID();
      t.slug = slug;
      t.name = displayName.isBlank() ? "Demo Store" : displayName;
      t.modulesJson = "{}";
      t.subscriptionPlan = TenantEntity.SubscriptionPlan.STARTER;
      t.createdAt = Instant.now();
      tenant = tenants.save(t);
      createdTenant = true;
    }

    boolean createdUser = false;
    UserEntity user = users.findByEmailIgnoreCase(ownerEmail).orElse(null);
    if (user == null) {
      UserEntity u = new UserEntity();
      u.id = UUID.randomUUID();
      u.email = ownerEmail;
      u.passwordHash = passwordHasher.hash(ownerPassword);
      u.createdAt = Instant.now();
      user = users.save(u);
      createdUser = true;
    }

    UUID tenantId = tenant.id;
    boolean hasMerchantMembership =
        memberships.findAllByUserId(user.id).stream()
            .anyMatch(
                m ->
                    tenantId.equals(m.tenantId)
                        && (m.role == Role.MERCHANT_OWNER || m.role == Role.MERCHANT_STAFF));
    boolean createdMembership = false;
    if (!hasMerchantMembership) {
      MembershipEntity m = new MembershipEntity();
      m.id = UUID.randomUUID();
      m.userId = user.id;
      m.tenantId = tenant.id;
      m.role = Role.MERCHANT_OWNER;
      m.createdAt = Instant.now();
      memberships.save(m);
      createdMembership = true;
    }

    boolean createdShopSettings = shopSettings.findByTenantId(tenant.id).isEmpty();
    if (createdShopSettings) {
      shopSettings.save(demoShopSettings(tenant.id));
    } else {
      shopSettings.findByTenantId(tenant.id).ifPresent(this::ensureDemoLocationAndHybridType);
    }

    ensureDemoCatalogue(tenant.id);

    // Ensure salon staff and availability for demo salon merchants
    boolean createdSalonStaffAndAvailability = ensureSalonStaffAndAvailability(tenant.id);

    // STANDARD unlocks Team payroll / Insights like Wheel Hub Gold for local demos.
    if (!subscriptions.hasEffectiveSubscription(tenant.id)) {
      subscriptions.forceActivatePlan(tenant.id, TenantEntity.SubscriptionPlan.STANDARD);
      log.info("Demo merchant subscription activated: STANDARD for slug={}", slug);
    }

    if (createdTenant || createdUser || createdMembership || createdShopSettings || createdSalonStaffAndAvailability) {
      log.info(
          "Demo merchant ready (bootstrap): storefront=/m/{} admin=/m/{}/admin ownerEmail={}",
          slug,
          slug,
          ownerEmail);
    }
  }

  private ShopSettingsEntity demoShopSettings(UUID tenantId) {
    ShopSettingsEntity row = ShopSettingsDefaults.newRowForTenant(tenantId);
    applyDemoMarketplaceDefaults(row);
    return row;
  }

  private void ensureDemoLocationAndHybridType(ShopSettingsEntity s) {
    boolean dirty = false;
    if (s.storeLat == null || s.storeLng == null) {
      s.storeLat = -26.2041;
      s.storeLng = 28.0473;
      dirty = true;
    }
    if (SalonAccessService.SHOP_NORMAL.equals(SalonAccessService.normalizedShopType(s.shopType))) {
      s.shopType = SalonAccessService.SHOP_SALON_AND_STORE;
      dirty = true;
    }
    if (s.storeName == null || s.storeName.isBlank()) {
      s.storeName = displayName.isBlank() ? "Demo Store" : displayName;
      dirty = true;
    }
    if (dirty) shopSettings.save(s);
  }

  private void applyDemoMarketplaceDefaults(ShopSettingsEntity row) {
    row.storeLat = -26.2041;
    row.storeLng = 28.0473;
    row.shopType = SalonAccessService.SHOP_SALON_AND_STORE;
    row.storeName = displayName.isBlank() ? "Demo Store" : displayName;
  }

  private void ensureDemoCatalogue(UUID tenantId) {
    if (products.countByTenantId(tenantId) == 0) {
      ProductEntity p = new ProductEntity();
      p.id = UUID.randomUUID();
      p.tenantId = tenantId;
      p.name = "Demo candle";
      p.category = "Home";
      p.priceZar = BigDecimal.valueOf(120);
      p.stock = 12;
      p.imageUrl = "";
      p.imagePath = "";
      p.archivedAt = null;
      p.createdAt = Instant.now();
      products.save(p);
    }
    if (salonServices.countByTenantIdAndActiveTrue(tenantId) == 0) {
      SalonServiceEntity s = new SalonServiceEntity();
      s.id = UUID.randomUUID();
      s.tenantId = tenantId;
      s.name = "Demo blow-dry";
      s.description = "Sample salon service for nearby search.";
      s.durationMinutes = 45;
      s.priceZar = BigDecimal.valueOf(250);
      s.imageUrl = "";
      s.imagePath = "";
      s.active = Boolean.TRUE;
      s.createdAt = Instant.now();
      salonServices.save(s);
    }
  }

  private boolean ensureSalonStaffAndAvailability(UUID tenantId) {
    List<SalonStaffEntity> existingStaff = salonStaff.findActiveByTenant(tenantId);
    List<SalonStaffAvailabilityEntity> existingAvailability =
        salonAvailability.findByTenantIdOrderByStaffIdAscDayOfWeekAscStartTimeAsc(tenantId);

    if (!existingStaff.isEmpty() && !existingAvailability.isEmpty()) {
      return false; // Already configured
    }

    if (!existingStaff.isEmpty() && existingAvailability.isEmpty()) {
      // Staff exists but no availability, add availability for existing staff
      for (SalonStaffEntity staff : existingStaff) {
        createWeekdayAvailability(tenantId, staff.id);
      }
      log.info("Created demo salon availability for existing staff, tenant {}", tenantId);
      return true;
    }

    // No staff exists, create default staff member with availability
    SalonStaffEntity staff = new SalonStaffEntity();
    staff.id = UUID.randomUUID();
    staff.tenantId = tenantId;
    staff.displayName = "Demo Staff Member";
    staff.active = true;
    staff.createdAt = Instant.now();
    staff = salonStaff.save(staff);

    // Create availability for Monday to Friday (9am-5pm) and Saturday (9am-1pm)
    createWeekdayAvailability(tenantId, staff.id);

    log.info("Created demo salon staff and availability for tenant {}", tenantId);
    return true;
  }

  private void createWeekdayAvailability(UUID tenantId, UUID staffId) {
    // Monday to Friday: 9am - 5pm
    for (int dayOfWeek = 1; dayOfWeek <= 5; dayOfWeek++) {
      createAvailabilitySlot(tenantId, staffId, dayOfWeek, LocalTime.of(9, 0), LocalTime.of(17, 0));
    }
    // Saturday: 9am - 1pm
    createAvailabilitySlot(tenantId, staffId, 6, LocalTime.of(9, 0), LocalTime.of(13, 0));
  }

  private void createAvailabilitySlot(UUID tenantId, UUID staffId, int dayOfWeek, LocalTime start, LocalTime end) {
    SalonStaffAvailabilityEntity avail = new SalonStaffAvailabilityEntity();
    avail.id = UUID.randomUUID();
    avail.tenantId = tenantId;
    avail.staffId = staffId;
    avail.dayOfWeek = dayOfWeek;
    avail.startTime = start;
    avail.endTime = end;
    avail.createdAt = Instant.now();
    salonAvailability.save(avail);
  }
}
