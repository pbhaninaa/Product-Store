package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.entities.PromotionEntity;
import com.productstore.platform.repositories.PromotionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {
  private final PromotionRepository promotions;

  public PromotionService(PromotionRepository promotions) {
    this.promotions = promotions;
  }

  @Transactional
  public PromotionEntity create(UUID tenantId, PromotionEntity body) {
    validateAndNormalize(body);
    body.id = null;
    body.tenantId = tenantId;
    if (body.usageCount == null) body.usageCount = 0;
    return promotions.save(body);
  }

  @Transactional
  public PromotionEntity update(UUID tenantId, UUID id, PromotionEntity body) {
    PromotionEntity existing =
        promotions
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("promotion_not_found"));
    validateAndNormalize(body);
    existing.title = body.title;
    existing.description = body.description;
    existing.discountType = body.discountType;
    existing.discountValue = body.discountValue;
    existing.minimumOrderValue = body.minimumOrderValue;
    existing.startDate = body.startDate;
    existing.endDate = body.endDate;
    existing.active = body.active;
    existing.applicableCategories = body.applicableCategories;
    existing.usageLimit = body.usageLimit;
    return promotions.save(existing);
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    PromotionEntity existing =
        promotions
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("promotion_not_found"));
    promotions.delete(existing);
  }

  public List<PromotionEntity> listMine(UUID tenantId) {
    return promotions.findByTenantIdOrderByStartDateDesc(tenantId);
  }

  public List<PromotionEntity> listActiveToday(UUID tenantId) {
    LocalDate today = LocalDate.now();
    return promotions
        .findByTenantIdAndActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
            tenantId, today, today);
  }

  /**
   * Wheel Hub applies the discount at checkout time and does not persist the promo on the payment
   * row. Same here: compute ZAR off the given amount and bump usage when a promo is applied.
   */
  @Transactional
  public BigDecimal applyDiscount(UUID tenantId, UUID promoId, BigDecimal amount) {
    if (promoId == null || amount == null) {
      return BigDecimal.ZERO;
    }
    PromotionEntity promo =
        promotions
            .findByIdAndTenantId(promoId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("promotion_not_found"));
    LocalDate today = LocalDate.now();
    if (!promo.active
        || promo.startDate == null
        || promo.endDate == null
        || today.isBefore(promo.startDate)
        || today.isAfter(promo.endDate)) {
      throw new IllegalArgumentException("promotion_not_active");
    }
    if (promo.usageLimit != null && promo.usageCount != null && promo.usageCount >= promo.usageLimit) {
      throw new IllegalArgumentException("promotion_exhausted");
    }
    BigDecimal min =
        promo.minimumOrderValue == null
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(promo.minimumOrderValue).setScale(2, RoundingMode.HALF_UP);
    if (amount.compareTo(min) < 0) {
      throw new IllegalArgumentException("promotion_minimum_not_met");
    }
    BigDecimal discount;
    if ("PERCENTAGE".equalsIgnoreCase(promo.discountType)) {
      discount =
          amount
              .multiply(BigDecimal.valueOf(promo.discountValue))
              .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    } else {
      discount = BigDecimal.valueOf(promo.discountValue).setScale(2, RoundingMode.HALF_UP);
    }
    if (discount.compareTo(amount) > 0) {
      discount = amount;
    }
    if (discount.compareTo(BigDecimal.ZERO) < 0) {
      discount = BigDecimal.ZERO;
    }
    promo.usageCount = (promo.usageCount == null ? 0 : promo.usageCount) + 1;
    promotions.save(promo);
    return discount;
  }

  public Map<String, Object> toMap(PromotionEntity p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", p.id.toString());
    m.put("title", p.title);
    m.put("description", p.description == null ? "" : p.description);
    m.put("discountType", p.discountType);
    m.put("discountValue", p.discountValue);
    m.put("minimumOrderValue", p.minimumOrderValue);
    m.put("startDate", p.startDate != null ? p.startDate.toString() : null);
    m.put("endDate", p.endDate != null ? p.endDate.toString() : null);
    m.put("active", p.active);
    m.put("applicableCategories", p.applicableCategories == null ? "" : p.applicableCategories);
    m.put("usageLimit", p.usageLimit);
    m.put("usageCount", p.usageCount);
    return m;
  }

  public List<Map<String, Object>> toMaps(List<PromotionEntity> rows) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (PromotionEntity p : rows) {
      out.add(toMap(p));
    }
    return out;
  }

  private void validateAndNormalize(PromotionEntity body) {
    if (body == null) {
      throw new IllegalArgumentException("Promotion body is required");
    }
    String title = body.title != null ? body.title.trim() : "";
    if (title.isEmpty()) {
      throw new IllegalArgumentException("Title is required");
    }
    if (title.length() > 200) {
      throw new IllegalArgumentException("Title must be 200 characters or fewer");
    }
    body.title = title;
    if (body.description != null) {
      body.description = body.description.trim();
    }
    String discountType = body.discountType != null ? body.discountType.trim().toUpperCase() : "";
    if (!"PERCENTAGE".equals(discountType) && !"FIXED".equals(discountType)) {
      throw new IllegalArgumentException("Discount type must be PERCENTAGE or FIXED");
    }
    body.discountType = discountType;
    if (body.discountValue == null || body.discountValue <= 0) {
      throw new IllegalArgumentException("Discount value must be greater than zero");
    }
    if ("PERCENTAGE".equals(discountType) && body.discountValue > 100) {
      throw new IllegalArgumentException("Percentage discount cannot exceed 100");
    }
    if (body.startDate == null || body.endDate == null) {
      throw new IllegalArgumentException("Start and end dates are required");
    }
    if (body.endDate.isBefore(body.startDate)) {
      throw new IllegalArgumentException("End date must be on or after start date");
    }
    if (body.applicableCategories != null) {
      String cats = body.applicableCategories.trim();
      body.applicableCategories = cats.isEmpty() ? null : cats;
    }
  }
}
