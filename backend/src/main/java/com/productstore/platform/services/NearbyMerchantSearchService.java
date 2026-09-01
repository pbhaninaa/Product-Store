package com.productstore.platform.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.productstore.platform.controllers.PublicShopSettingsController;
import com.productstore.platform.entities.ProductEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.OrderReviewRepository;
import com.productstore.platform.repositories.ProductRepository;
import com.productstore.platform.repositories.SalonBookingReviewRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;

import org.springframework.stereotype.Service;

/**
 * Nearby merchant stream for CLIENT search: every shop/salon in radius, with ratings so the client can pick.
 */
@Service
public class NearbyMerchantSearchService {
  public static final double DEFAULT_RADIUS_KM = 50d;
  public static final double MAX_RADIUS_KM = 200d;

  public enum Kind {
    shop,
    salon
  }

  private final ShopSettingsRepository shopSettings;
  private final TenantRepository tenants;
  private final ProductRepository products;
  private final SalonServiceRepository salonServices;
  private final MerchantSubscriptionService subscriptions;
  private final OrderReviewRepository orderReviews;
  private final SalonBookingReviewRepository salonReviews;

  public NearbyMerchantSearchService(
      ShopSettingsRepository shopSettings,
      TenantRepository tenants,
      ProductRepository products,
      SalonServiceRepository salonServices,
      MerchantSubscriptionService subscriptions,
      OrderReviewRepository orderReviews,
      SalonBookingReviewRepository salonReviews) {
    this.shopSettings = shopSettings;
    this.tenants = tenants;
    this.products = products;
    this.salonServices = salonServices;
    this.subscriptions = subscriptions;
    this.orderReviews = orderReviews;
    this.salonReviews = salonReviews;
  }

  public static Kind parseKind(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("kind_required");
    }
    String k = raw.trim().toLowerCase(Locale.ROOT);
    if ("shop".equals(k) || "store".equals(k)) return Kind.shop;
    if ("salon".equals(k)) return Kind.salon;
    throw new IllegalArgumentException("invalid_kind");
  }

  public static double normalizeRadiusKm(Double raw) {
    if (raw == null || !Double.isFinite(raw) || raw <= 0) {
      return DEFAULT_RADIUS_KM;
    }
    return Math.min(MAX_RADIUS_KM, raw);
  }

  public List<Map<String, Object>> listOfferings(
      Kind kind, double latitude, double longitude, double radiusKm, String q) {
    List<Candidate> candidates = candidatesInRadius(kind, latitude, longitude, radiusKm);
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (Candidate c : candidates) {
      for (Offering o : c.offerings) {
        names.add(o.name);
      }
    }
    String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    List<Map<String, Object>> out = new ArrayList<>();
    for (String name : names) {
      if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", name);
      out.add(row);
    }
    out.sort(Comparator.comparing(m -> String.valueOf(m.get("name")), String.CASE_INSENSITIVE_ORDER));
    return out;
  }

  public List<Map<String, Object>> listMerchants(
      Kind kind, double latitude, double longitude, double radiusKm, String namesCsv) {
    List<String> required = parseNames(namesCsv);
    List<Candidate> candidates = candidatesInRadius(kind, latitude, longitude, radiusKm);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Candidate c : candidates) {
      List<Offering> shown =
          required.isEmpty() ? c.offerings : matchAllRequired(c.offerings, required);
      if (!required.isEmpty() && shown.isEmpty()) continue;
      out.add(toMerchantCard(c, shown));
    }
    out.sort(
        Comparator.comparingDouble((Map<String, Object> m) -> -((Number) m.get("averageRating")).doubleValue())
            .thenComparingLong(m -> -((Number) m.get("reviewCount")).longValue())
            .thenComparingDouble(m -> ((Number) m.get("distanceKm")).doubleValue()));
    return out;
  }

  private List<Candidate> candidatesInRadius(
      Kind kind, double latitude, double longitude, double radiusKm) {
    requireFinite(latitude, longitude);
    double radius = normalizeRadiusKm(radiusKm);
    List<ShopSettingsEntity> shops = shopSettings.findAllWithStoreCoordinates();
    Map<UUID, TenantEntity> tenantById = new HashMap<>();
    for (TenantEntity t : tenants.findAll()) {
      tenantById.put(t.id, t);
    }

    List<ShopSettingsEntity> inRange = new ArrayList<>();
    Map<UUID, Double> distanceByTenant = new HashMap<>();
    for (ShopSettingsEntity s : shops) {
      if (s.storeLat == null || s.storeLng == null) continue;
      if (!shopTypeMatches(kind, SalonAccessService.normalizedShopType(s.shopType))) continue;
      if (!subscriptions.hasEffectiveSubscription(s.tenantId)) continue;
      TenantEntity tenant = tenantById.get(s.tenantId);
      if (tenant == null) continue;
      double km = haversineKm(latitude, longitude, s.storeLat, s.storeLng);
      if (km > radius + 0.01) continue;
      inRange.add(s);
      distanceByTenant.put(s.tenantId, roundKm(km));
    }
    if (inRange.isEmpty()) return List.of();

    Set<UUID> tenantIds = inRange.stream().map(s -> s.tenantId).collect(Collectors.toSet());
    Map<UUID, List<Offering>> offeringsByTenant = loadOfferings(kind, tenantIds);

    List<Candidate> out = new ArrayList<>();
    for (ShopSettingsEntity s : inRange) {
      List<Offering> offs = offeringsByTenant.getOrDefault(s.tenantId, List.of());
      TenantEntity tenant = tenantById.get(s.tenantId);
      out.add(
          new Candidate(
              tenant,
              s,
              distanceByTenant.get(s.tenantId),
              offs));
    }
    return out;
  }

  private Map<UUID, List<Offering>> loadOfferings(Kind kind, Collection<UUID> tenantIds) {
    Map<UUID, List<Offering>> map = new HashMap<>();
    if (tenantIds == null || tenantIds.isEmpty()) return map;
    if (kind == Kind.shop) {
      for (ProductEntity p : products.findByTenantIdInAndArchivedAtIsNull(tenantIds)) {
        if (p.name == null || p.name.isBlank()) continue;
        map.computeIfAbsent(p.tenantId, k -> new ArrayList<>())
            .add(new Offering(p.id, p.name.trim(), p.priceZar, "product"));
      }
    } else {
      for (SalonServiceEntity s : salonServices.findByTenantIdInAndActiveTrue(tenantIds)) {
        if (s.name == null || s.name.isBlank()) continue;
        map.computeIfAbsent(s.tenantId, k -> new ArrayList<>())
            .add(new Offering(s.id, s.name.trim(), s.priceZar, "service"));
      }
    }
    return map;
  }

  private Map<String, Object> toMerchantCard(Candidate c, List<Offering> matched) {
    ShopSettingsEntity s = c.settings;
    TenantEntity t = c.tenant;
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("merchantSlug", t.slug);
    m.put("storeName", storeName(s, t));
    m.put("distanceKm", c.distanceKm);
    m.put("shopType", SalonAccessService.normalizedShopType(s.shopType));
    m.put("logoUrl", PublicShopSettingsController.publicLogoUrl(t.slug, s));
    m.put(
        "acceptCustomerPeach",
        s.acceptCustomerPeach == null || Boolean.TRUE.equals(s.acceptCustomerPeach));
    m.put(
        "acceptCustomerEft",
        s.acceptCustomerEft == null || Boolean.TRUE.equals(s.acceptCustomerEft));
    m.put(
        "acceptCustomerCash",
        s.acceptCustomerCash == null || Boolean.TRUE.equals(s.acceptCustomerCash));
    RatingStats rating = combinedRating(t.id);
    m.put("averageRating", rating.average);
    m.put("reviewCount", rating.count);
    List<Map<String, Object>> offeringMaps = new ArrayList<>();
    for (Offering o : matched) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", o.id.toString());
      row.put("name", o.name);
      row.put("priceZar", o.priceZar == null ? "0" : o.priceZar.toPlainString());
      row.put("kind", o.kind);
      offeringMaps.add(row);
    }
    m.put("offerings", offeringMaps);
    return m;
  }

  private RatingStats combinedRating(UUID tenantId) {
    Double orderAvg = orderReviews.averageRatingByTenant(tenantId);
    long orderCount = orderReviews.countByTenantId(tenantId);
    Double salonAvg = salonReviews.averageRatingByTenant(tenantId);
    long salonCount = salonReviews.countByTenantId(tenantId);
    long total = orderCount + salonCount;
    if (total <= 0) return new RatingStats(0d, 0);
    double oa = orderAvg == null ? 0d : orderAvg;
    double sa = salonAvg == null ? 0d : salonAvg;
    double weighted = (oa * orderCount + sa * salonCount) / total;
    return new RatingStats(roundKm(weighted), total);
  }

  private static List<Offering> matchAllRequired(List<Offering> offerings, List<String> required) {
    Map<String, Offering> byKey = new HashMap<>();
    for (Offering o : offerings) {
      byKey.putIfAbsent(normalizeName(o.name), o);
    }
    List<Offering> matched = new ArrayList<>();
    for (String want : required) {
      Offering hit = byKey.get(normalizeName(want));
      if (hit == null) return List.of();
      matched.add(hit);
    }
    return matched;
  }

  private static List<String> parseNames(String csv) {
    if (csv == null || csv.isBlank()) return List.of();
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String part : csv.split(",")) {
      String n = part == null ? "" : part.trim();
      if (!n.isEmpty()) out.add(n);
    }
    return new ArrayList<>(out);
  }

  private static boolean shopTypeMatches(Kind kind, String shopType) {
    if (kind == Kind.shop) {
      return SalonAccessService.SHOP_NORMAL.equals(shopType)
          || SalonAccessService.SHOP_SALON_AND_STORE.equals(shopType);
    }
    return SalonAccessService.SHOP_SALON_AND_STORE.equals(shopType)
        || SalonAccessService.SHOP_SALON_ONLY.equals(shopType);
  }

  private static String storeName(ShopSettingsEntity s, TenantEntity t) {
    String n = s.storeName == null ? "" : s.storeName.trim();
    if (n.length() >= 2) return n;
    return t.name == null || t.name.isBlank() ? t.slug : t.name;
  }

  private static String normalizeName(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private static void requireFinite(double latitude, double longitude) {
    if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
      throw new IllegalArgumentException("location_required");
    }
    if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
      throw new IllegalArgumentException("invalid_location");
    }
  }

  static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
    double R = 6371.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2)
                * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  private static double roundKm(double km) {
    return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private record Offering(UUID id, String name, BigDecimal priceZar, String kind) {}

  private record RatingStats(double average, long count) {}

  private record Candidate(
      TenantEntity tenant, ShopSettingsEntity settings, double distanceKm, List<Offering> offerings) {}
}
