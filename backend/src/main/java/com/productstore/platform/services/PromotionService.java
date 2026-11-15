package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  /** One priced cart line used to decide whether (and how much of) a promotion applies. */
  public record PricedLine(String category, BigDecimal amount) {}

  /** A promotion that is valid for a given cart, with the ZAR discount it gives. */
  public record PromotionQuote(PromotionEntity promo, BigDecimal discount) {}

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
   * Resolves the promotion for a checkout or booking. When {@code promoId} is given it must belong to
   * the tenant and be valid today for these lines, otherwise an {@link IllegalArgumentException} is
   * thrown. When it is null the best currently-valid promotion is picked automatically. The discount is
   * always computed here from server-side prices, never taken from the client.
   */
  @Transactional(readOnly = true)
  public Optional<PromotionQuote> resolve(UUID tenantId, UUID promoId, List<PricedLine> lines) {
    if (promoId != null) {
      return Optional.of(requireEligible(tenantId, promoId, lines));
    }
    return bestFor(tenantId, lines);
  }

  /** The currently-valid promotion that gives the largest discount for these lines, if any. */
  @Transactional(readOnly = true)
  public Optional<PromotionQuote> bestFor(UUID tenantId, List<PricedLine> lines) {
    if (tenantId == null) return Optional.empty();
    LocalDate today = LocalDate.now();
    PromotionQuote best = null;
    for (PromotionEntity promo : listActiveToday(tenantId)) {
      if (ineligibility(promo, lines, today) != null) continue;
      BigDecimal discount = computeDiscount(promo, lines);
      if (discount.compareTo(BigDecimal.ZERO) <= 0) continue;
      if (best == null || discount.compareTo(best.discount()) > 0) {
        best = new PromotionQuote(promo, discount);
      }
    }
    return Optional.ofNullable(best);
  }

  /** Validates one promotion for these lines; throws with a stable error code when it does not apply. */
  @Transactional(readOnly = true)
  public PromotionQuote requireEligible(UUID tenantId, UUID promoId, List<PricedLine> lines) {
    if (tenantId == null || promoId == null) {
      throw new IllegalArgumentException("promotion_not_found");
    }
    PromotionEntity promo =
        promotions
            .findByIdAndTenantId(promoId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("promotion_not_found"));
    String problem = ineligibility(promo, lines, LocalDate.now());
    if (problem != null) {
      throw new IllegalArgumentException(problem);
    }
    return new PromotionQuote(promo, computeDiscount(promo, lines));
  }

  /** Counts one use of an applied promotion (call once the order or booking is saved). */
  @Transactional
  public void recordUse(PromotionEntity promo) {
    if (promo == null) return;
    promo.usageCount = (promo.usageCount == null ? 0 : promo.usageCount) + 1;
    promotions.save(promo);
  }

  /** Public shape of a quote for the storefront order summary. */
  public Map<String, Object> toQuoteMap(Optional<PromotionQuote> quote, BigDecimal subtotal) {
    Map<String, Object> m = new LinkedHashMap<>();
    BigDecimal sub = subtotal == null ? BigDecimal.ZERO : subtotal.setScale(2, RoundingMode.HALF_UP);
    m.put("subtotalZar", sub.toPlainString());
    if (quote.isPresent()) {
      PromotionQuote q = quote.get();
      m.put("promoId", q.promo().id.toString());
      m.put("title", q.promo().title);
      m.put("discountType", q.promo().discountType);
      m.put("discountValue", q.promo().discountValue);
      m.put("discountZar", q.discount().toPlainString());
    } else {
      m.put("promoId", null);
      m.put("title", null);
      m.put("discountZar", "0.00");
    }
    return m;
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

  private static String ineligibility(PromotionEntity promo, List<PricedLine> lines, LocalDate today) {
    if (!promo.active
        || promo.startDate == null
        || promo.endDate == null
        || today.isBefore(promo.startDate)
        || today.isAfter(promo.endDate)) {
      return "promotion_not_active";
    }
    if (promo.usageLimit != null
        && promo.usageCount != null
        && promo.usageCount >= promo.usageLimit) {
      return "promotion_exhausted";
    }
    BigDecimal min =
        promo.minimumOrderValue == null
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(promo.minimumOrderValue).setScale(2, RoundingMode.HALF_UP);
    if (totalOf(lines).compareTo(min) < 0) {
      return "promotion_minimum_not_met";
    }
    if (eligibleAmount(promo, lines).compareTo(BigDecimal.ZERO) <= 0) {
      return "promotion_not_applicable";
    }
    return null;
  }

  private static BigDecimal computeDiscount(PromotionEntity promo, List<PricedLine> lines) {
    BigDecimal base = eligibleAmount(promo, lines);
    double value = promo.discountValue == null ? 0d : promo.discountValue;
    BigDecimal discount;
    if ("PERCENTAGE".equalsIgnoreCase(promo.discountType)) {
      discount =
          base.multiply(BigDecimal.valueOf(value))
              .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    } else {
      discount = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
    if (discount.compareTo(base) > 0) discount = base;
    if (discount.compareTo(BigDecimal.ZERO) < 0) discount = BigDecimal.ZERO;
    return discount.setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal totalOf(List<PricedLine> lines) {
    BigDecimal sum = BigDecimal.ZERO;
    if (lines == null) return sum.setScale(2, RoundingMode.HALF_UP);
    for (PricedLine l : lines) {
      if (l != null && l.amount() != null) sum = sum.add(l.amount());
    }
    return sum.setScale(2, RoundingMode.HALF_UP);
  }

  /** Amount of the cart a promotion covers: every line, or only lines in its listed categories. */
  private static BigDecimal eligibleAmount(PromotionEntity promo, List<PricedLine> lines) {
    Set<String> cats = categorySet(promo.applicableCategories);
    if (cats.isEmpty()) return totalOf(lines);
    BigDecimal sum = BigDecimal.ZERO;
    if (lines != null) {
      for (PricedLine l : lines) {
        if (l == null || l.amount() == null) continue;
        String c = l.category() == null ? "" : l.category().trim().toLowerCase(Locale.ROOT);
        if (!c.isEmpty() && cats.contains(c)) sum = sum.add(l.amount());
      }
    }
    return sum.setScale(2, RoundingMode.HALF_UP);
  }

  private static Set<String> categorySet(String raw) {
    Set<String> out = new HashSet<>();
    if (raw == null) return out;
    for (String part : raw.split(",")) {
      String c = part.trim().toLowerCase(Locale.ROOT);
      if (!c.isEmpty()) out.add(c);
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
