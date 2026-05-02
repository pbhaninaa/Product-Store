package com.productstore.platform.services;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.repositories.ShopSettingsRepository;

import org.springframework.stereotype.Service;

/**
 * Parses {@link ShopSettingsEntity#openingHoursJson} and resolves the shop-level open window for a day.
 * When JSON is null/blank/empty array, shop hours do not restrict slots (staff availability only).
 */
@Service
public class ShopOpeningHoursService {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public enum ConstraintKind {
    /** No shop-level hours configured; do not clip staff windows. */
    UNRESTRICTED,
    /** Shop explicitly closed this day (hours configured but no window for this weekday). */
    CLOSED,
    /** Intersect staff availability with [start, end]. */
    WINDOW
  }

  public record ShopDayConstraint(ConstraintKind kind, LocalTime start, LocalTime end) {
    public static ShopDayConstraint unrestricted() {
      return new ShopDayConstraint(ConstraintKind.UNRESTRICTED, null, null);
    }

    public static ShopDayConstraint closed() {
      return new ShopDayConstraint(ConstraintKind.CLOSED, null, null);
    }

    public static ShopDayConstraint window(LocalTime start, LocalTime end) {
      return new ShopDayConstraint(ConstraintKind.WINDOW, start, end);
    }
  }

  private final ShopSettingsRepository settings;

  public ShopOpeningHoursService(ShopSettingsRepository settings) {
    this.settings = settings;
  }

  public ShopDayConstraint resolveForTenantDay(UUID tenantId, int isoDayOfWeek) {
    ShopSettingsEntity s = settings.findByTenantId(tenantId).orElse(null);
    if (s == null || s.openingHoursJson == null || s.openingHoursJson.isBlank()) {
      return ShopDayConstraint.unrestricted();
    }
    String raw = s.openingHoursJson.trim();
    if (raw.isEmpty() || "[]".equals(raw) || "{}".equals(raw)) {
      return ShopDayConstraint.unrestricted();
    }
    try {
      JsonNode root = MAPPER.readTree(raw);
      if (!root.isArray() || root.isEmpty()) {
        return ShopDayConstraint.unrestricted();
      }
      for (JsonNode n : root) {
        if (n == null || !n.isObject()) continue;
        JsonNode dowNode = n.get("dayOfWeek");
        if (dowNode == null || !dowNode.isInt()) continue;
        if (dowNode.intValue() != isoDayOfWeek) continue;
        JsonNode closed = n.get("closed");
        if (closed != null && closed.isBoolean() && closed.booleanValue()) {
          return ShopDayConstraint.closed();
        }
        String startS = text(n, "start");
        String endS = text(n, "end");
        if (startS.isEmpty() || endS.isEmpty()) {
          return ShopDayConstraint.closed();
        }
        LocalTime st = LocalTime.parse(startS.trim());
        LocalTime en = LocalTime.parse(endS.trim());
        if (!en.isAfter(st)) {
          return ShopDayConstraint.closed();
        }
        return ShopDayConstraint.window(st, en);
      }
      // Hours exist for the tenant but this weekday is not listed -> treat as closed.
      return ShopDayConstraint.closed();
    } catch (Exception e) {
      return ShopDayConstraint.unrestricted();
    }
  }

  /** Validates and normalizes JSON for storage (compact array string). */
  public String normalizeJsonForStorage(String raw) {
    if (raw == null || raw.isBlank()) {
      return "[]";
    }
    final JsonNode root;
    try {
      root = MAPPER.readTree(raw);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("invalid_opening_hours_json");
    }
    if (!root.isArray()) {
      throw new IllegalArgumentException("invalid_opening_hours_json");
    }
    List<JsonNode> kept = new ArrayList<>();
    for (JsonNode n : root) {
      if (n == null || !n.isObject()) continue;
      JsonNode dowNode = n.get("dayOfWeek");
      if (dowNode == null || !dowNode.isInt()) continue;
      int dow = dowNode.intValue();
      if (dow < 1 || dow > 7) continue;
      JsonNode closed = n.get("closed");
      if (closed != null && closed.isBoolean() && closed.booleanValue()) {
        kept.add(
            MAPPER
                .createObjectNode()
                .put("dayOfWeek", dow)
                .put("closed", true));
        continue;
      }
      String startS = text(n, "start");
      String endS = text(n, "end");
      if (startS.isEmpty() || endS.isEmpty()) continue;
      LocalTime st = LocalTime.parse(startS.trim());
      LocalTime en = LocalTime.parse(endS.trim());
      if (!en.isAfter(st)) continue;
      kept.add(
          MAPPER
              .createObjectNode()
              .put("dayOfWeek", dow)
              .put("start", st.toString())
              .put("end", en.toString()));
    }
    try {
      return MAPPER.writeValueAsString(kept);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("invalid_opening_hours_json");
    }
  }

  private static String text(JsonNode n, String field) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull()) return "";
    return String.valueOf(v.asText("")).trim();
  }

  /**
   * Intersects aggregate staff availability bounds with shop opening hours for that weekday.
   *
   * @return empty if the shop is closed for the day; otherwise min/max local times in the shop zone.
   */
  public Optional<LocalTime[]> intersectStaffDayBounds(
      UUID tenantId, int isoDayOfWeek, LocalTime staffMin, LocalTime staffMax) {
    if (!staffMax.isAfter(staffMin)) {
      return Optional.empty();
    }
    ShopDayConstraint shop = resolveForTenantDay(tenantId, isoDayOfWeek);
    if (shop.kind() == ConstraintKind.CLOSED) {
      return Optional.empty();
    }
    LocalTime slotMin = staffMin;
    LocalTime slotMax = staffMax;
    if (shop.kind() == ConstraintKind.WINDOW) {
      LocalTime s0 = shop.start();
      LocalTime s1 = shop.end();
      slotMin = slotMin.isBefore(s0) ? s0 : slotMin;
      slotMax = slotMax.isAfter(s1) ? s1 : slotMax;
    }
    if (!slotMax.isAfter(slotMin)) {
      return Optional.empty();
    }
    return Optional.of(new LocalTime[] {slotMin, slotMax});
  }
}
