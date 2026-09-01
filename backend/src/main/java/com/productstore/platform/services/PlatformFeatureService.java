package com.productstore.platform.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.productstore.platform.entities.PlatformFeatureEntity;
import com.productstore.platform.repositories.PlatformFeatureRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformFeatureService {
  public static final String SHADOW_SUPPORT = "SHADOW_SUPPORT";
  public static final String MERCHANT_SIGNUP = "MERCHANT_SIGNUP";
  public static final String PUBLIC_CATALOG = "PUBLIC_CATALOG";
  public static final String WHATSAPP_ALERTS = "WHATSAPP_ALERTS";
  public static final String PAYMENTS = "PAYMENTS";
  public static final String PAYFAST = "PAYFAST";

  private static final List<String[]> CATALOG =
      List.of(
          new String[] {SHADOW_SUPPORT, "Allow support to shadow into merchant admin"},
          new String[] {MERCHANT_SIGNUP, "Public merchant self-signup at /signup"},
          new String[] {PUBLIC_CATALOG, "Public storefront catalog"},
          new String[] {WHATSAPP_ALERTS, "WhatsApp alert channel (plan feature still applies)"},
          new String[] {PAYMENTS, "In-app customer and subscription payments"},
          new String[] {PAYFAST, "PayFast hosted checkout (same rail as Wheel Hub)"});

  private final PlatformFeatureRepository features;

  public PlatformFeatureService(PlatformFeatureRepository features) {
    this.features = features;
  }

  @Transactional
  public List<Map<String, Object>> listOrBootstrap() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (String[] row : CATALOG) {
      PlatformFeatureEntity f =
          features
              .findByFeatureKey(row[0])
              .orElseGet(
                  () -> {
                    PlatformFeatureEntity n = new PlatformFeatureEntity();
                    n.featureKey = row[0];
                    n.description = row[1];
                    n.enabled = true;
                    n.updatedAt = Instant.now();
                    return features.save(n);
                  });
      out.add(toMap(f));
    }
    return out;
  }

  public boolean isEnabled(String key) {
    return features.findByFeatureKey(key).map(f -> f.enabled).orElse(true);
  }

  @Transactional
  public Map<String, Object> setEnabled(String key, boolean enabled) {
    String k = key == null ? "" : key.trim().toUpperCase();
    PlatformFeatureEntity f =
        features
            .findByFeatureKey(k)
            .orElseGet(
                () -> {
                  PlatformFeatureEntity n = new PlatformFeatureEntity();
                  n.featureKey = k;
                  n.description = "";
                  return n;
                });
    f.enabled = enabled;
    f.updatedAt = Instant.now();
    features.save(f);
    return toMap(f);
  }

  private static Map<String, Object> toMap(PlatformFeatureEntity f) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", f.featureKey);
    m.put("enabled", f.enabled);
    m.put("description", f.description);
    m.put("updatedAt", f.updatedAt != null ? f.updatedAt.toString() : null);
    return m;
  }
}
