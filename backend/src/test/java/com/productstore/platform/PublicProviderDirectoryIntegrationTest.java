package com.productstore.platform;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.PromotionEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.MerchantSubscriptionRepository;
import com.productstore.platform.repositories.PromotionRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.MerchantSubscriptionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The shareable /providers directory: open without sign-in, lists every active provider, never the platform. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicProviderDirectoryIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired TenantRepository tenantRepository;
  @Autowired ShopSettingsRepository shopSettingsRepository;
  @Autowired MembershipRepository membershipRepository;
  @Autowired UserRepository userRepository;
  @Autowired MerchantSubscriptionRepository subscriptionRepository;
  @Autowired MerchantSubscriptionService subscriptions;
  @Autowired PromotionRepository promotionRepository;

  @BeforeEach
  void seed() {
    promotionRepository.deleteAll();
    shopSettingsRepository.deleteAll();
    membershipRepository.deleteAll();
    subscriptionRepository.deleteAll();
    userRepository.deleteAll();
    tenantRepository.deleteAll();

    // Shop in Johannesburg, salon without a store location, and the internal platform tenant.
    provider("demo", "Demo Shop", "normal_store", -26.2041, 28.0473);
    UUID glow = provider("glow", "Glow Salon", "salon_only", null, null);
    provider("platform", "Platform", "normal_store", -26.2041, 28.0473);

    // One offer running today at the salon.
    PromotionEntity p = new PromotionEntity();
    p.tenantId = glow;
    p.title = "10% off";
    p.discountType = "PERCENTAGE";
    p.discountValue = 10d;
    p.startDate = LocalDate.now().minusDays(1);
    p.endDate = LocalDate.now().plusDays(1);
    p.active = true;
    promotionRepository.save(p);
  }

  @Test
  void reportsActivePromotionsPerProvider() throws Exception {
    mvc.perform(get("/api/public/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.merchantSlug == 'glow')].activePromotionCount").value(1))
        .andExpect(jsonPath("$[?(@.merchantSlug == 'demo')].activePromotionCount").value(0));
  }

  @Test
  void listsEveryActiveProviderWithoutSignIn() throws Exception {
    mvc.perform(get("/api/public/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[?(@.merchantSlug == 'platform')]", hasSize(0)))
        .andExpect(jsonPath("$[?(@.merchantSlug == 'glow')].kind").value("salon"))
        .andExpect(jsonPath("$[?(@.merchantSlug == 'demo')].kind").value("shop"));
  }

  @Test
  void listsNewProviderBeforeItSavesStoreSettings() throws Exception {
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = "fresh-spa";
    t.name = "Fresh Spa";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenantRepository.save(t);
    subscriptions.forceActivatePlan(t.id, TenantEntity.SubscriptionPlan.STARTER);

    mvc.perform(get("/api/public/providers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[?(@.merchantSlug == 'fresh-spa')].storeName").value("Fresh Spa"))
        .andExpect(jsonPath("$[?(@.merchantSlug == 'fresh-spa')].shopType").value("normal_store"));
  }

  @Test
  void filtersByKind() throws Exception {
    mvc.perform(get("/api/public/providers").param("kind", "salon"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].merchantSlug").value("glow"));
  }

  @Test
  void withLocation_nearestFirstAndUnlocatedLast() throws Exception {
    mvc.perform(get("/api/public/providers").param("latitude", "-25.7479").param("longitude", "28.2293"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].merchantSlug").value("demo"))
        .andExpect(jsonPath("$[1].merchantSlug").value("glow"))
        .andExpect(jsonPath("$[1].distanceKm").value(nullValue()));
  }

  @Test
  void halfALocationIsRejected() throws Exception {
    mvc.perform(get("/api/public/providers").param("latitude", "-26.2"))
        .andExpect(status().isBadRequest());
  }

  private UUID provider(String slug, String name, String shopType, Double lat, Double lng) {
    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = slug;
    t.name = name;
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenantRepository.save(t);
    subscriptions.forceActivatePlan(t.id, TenantEntity.SubscriptionPlan.STARTER);

    ShopSettingsEntity s = new ShopSettingsEntity();
    s.id = UUID.randomUUID();
    s.tenantId = t.id;
    s.deliveryFeeMode = "standard";
    s.deliveryFeeFlatZar = BigDecimal.ZERO;
    s.deliveryFeePerKmZar = BigDecimal.ZERO;
    s.eftBankInstructions = "";
    s.bankName = "";
    s.bankAccountHolder = "";
    s.bankAccountNumber = "";
    s.bankBranchCode = "";
    s.storeName = name;
    s.contactEmail = "a@b.co";
    s.contactPhone = "000";
    s.contactAddress = "";
    s.contactNotes = "";
    s.storeLogoUrl = "";
    s.storeHeroUrl = "";
    s.shopType = shopType;
    s.openingHoursJson = "[]";
    s.storeLat = lat;
    s.storeLng = lng;
    s.acceptCustomerOnline = true;
    s.acceptCustomerEft = true;
    s.acceptCustomerCash = true;
    s.createdAt = Instant.now();
    s.updatedAt = s.createdAt;
    shopSettingsRepository.save(s);
    return t.id;
  }
}
