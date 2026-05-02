package com.productstore.platform.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicCatalogControllerTest {
  @Autowired MockMvc mvc;
  @Autowired TenantRepository tenantRepository;
  @Autowired ProductRepository productRepository;
  @Autowired ShopSettingsRepository shopSettingsRepository;

  @BeforeEach
  void setup() {
    // H2 is in-memory but Spring may reuse the same context/db between tests.
    // Clean Flyway schema state to ensure deterministic setup.
    productRepository.deleteAll();
    shopSettingsRepository.deleteAll();
    tenantRepository.deleteAll();

    TenantEntity t = new TenantEntity();
    t.id = UUID.randomUUID();
    t.slug = "demo";
    t.name = "Demo";
    t.modulesJson = "{}";
    t.createdAt = Instant.now();
    tenantRepository.save(t);

    ProductEntity p = new ProductEntity();
    p.id = UUID.randomUUID();
    p.tenantId = t.id;
    p.name = "Apples";
    p.category = "Fruit";
    p.priceZar = new BigDecimal("10.00");
    p.imageUrl = "";
    p.imagePath = "";
    p.stock = 5;
    p.archivedAt = null;
    p.createdAt = Instant.now();
    productRepository.save(p);
  }

  @Test
  void catalogReturns200() throws Exception {
    String body =
        mvc.perform(get("/api/public/m/demo/catalog"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).contains("products");
    assertThat(body).contains("Apples");
  }

  @Test
  void shopSettingsReturns200() throws Exception {
    String body =
        mvc.perform(get("/api/public/m/demo/shop-settings"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).contains("storeName");
  }

  @Test
  void shopSettingsReturns200EvenWithInvalidBearerHeader() throws Exception {
    mvc.perform(
            get("/api/public/m/demo/shop-settings")
                .header("Authorization", "Bearer not-a-valid-jwt"))
        .andExpect(status().isOk());
  }
}

