package com.productstore.platform.constants;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What a merchant staff member (MERCHANT_STAFF) may do in the store admin. The store owner
 * (MERCHANT_OWNER) always has every permission.
 *
 * <p>Always owner-only, whatever is granted here: subscription &amp; billing, the store banking
 * details ({@code PUT /admin/store/banking}), adding/editing/removing staff, and editing anyone's
 * permissions. Always allowed to every staff member: notifications, help &amp; tickets, their own
 * expected income, and reading the store settings the admin needs to render.
 *
 * <p>Stored per employee as a comma-separated list ({@code employees.merchant_permissions}). A NULL
 * value means "the owner never changed it" and resolves to {@link #LEGACY_STAFF_DEFAULT}; an empty
 * string means "no optional access at all".
 */
public enum MerchantPermission {
  // ---- Areas ----
  /** Orders: list, line items, fulfilment updates, cancel an unpaid order. */
  ORDERS,
  /** Salon bookings: list and status changes. */
  BOOKINGS,
  /** Payments page (read-only view over orders and bookings). */
  PAYMENTS,
  /** Product catalogue: add, edit, images. */
  PRODUCTS,
  /** Salon services and staff schedules (availability). */
  SERVICES,
  /** Promotions. */
  PROMOTIONS,
  /** Payroll: team list, payment calculations, mark paid, assign / credit work. Not hiring or permissions. */
  TEAM_PAYROLL,
  /** Store contact, delivery, branding, shop type, opening hours. Never banking. */
  STORE_SETTINGS,
  /** Insights (sales statistics; reads the order list). */
  INSIGHTS,

  // ---- Risky actions (granted separately from the area) ----
  /** Confirm order payments, confirm booking cash codes, approve/reject booking EFT proof. */
  CONFIRM_PAYMENTS,
  /** Permanently delete orders, bookings, and products. */
  DELETE_RECORDS,
  /** Cancel bookings. */
  CANCEL_BOOKINGS;

  public static final List<MerchantPermission> ALL = List.of(values());

  public static final List<MerchantPermission> AREAS =
      List.of(
          ORDERS, BOOKINGS, PAYMENTS, PRODUCTS, SERVICES, PROMOTIONS, TEAM_PAYROLL, STORE_SETTINGS,
          INSIGHTS);

  public static final List<MerchantPermission> RISKY_ACTIONS =
      List.of(CONFIRM_PAYMENTS, DELETE_RECORDS, CANCEL_BOOKINGS);

  /**
   * Exactly what MERCHANT_STAFF could do before per-staff permissions existed (derived from the
   * owner/staff checks in each Admin*Controller):
   *
   * <ul>
   *   <li>Orders: list, items, fulfilment, cancel, confirm payment, permanent delete — staff allowed.
   *   <li>Bookings: list, status (incl. cancel), confirm cash, EFT decision, delete — staff allowed.
   *   <li>Promotions: all endpoints — staff allowed.
   *   <li>Payments and Insights pages only read the order / booking lists — staff allowed.
   *   <li>Products, salon services &amp; availability, team &amp; payroll, store-settings writes —
   *       owner-only, so not in the default.
   * </ul>
   */
  public static final Set<MerchantPermission> LEGACY_STAFF_DEFAULT =
      Collections.unmodifiableSet(
          EnumSet.of(
              ORDERS,
              BOOKINGS,
              PAYMENTS,
              PROMOTIONS,
              INSIGHTS,
              CONFIRM_PAYMENTS,
              DELETE_RECORDS,
              CANCEL_BOOKINGS));

  /** Resolves a stored value: NULL keeps today's staff access; anything else is parsed as-is. */
  public static Set<MerchantPermission> resolveStored(String raw) {
    if (raw == null) return EnumSet.copyOf(LEGACY_STAFF_DEFAULT);
    return parseCsv(raw);
  }

  /** Parses a comma-separated list, ignoring blanks and unknown names. */
  public static Set<MerchantPermission> parseCsv(String raw) {
    Set<MerchantPermission> out = EnumSet.noneOf(MerchantPermission.class);
    if (raw == null || raw.isBlank()) return out;
    for (String part : raw.split(",")) {
      String p = part.trim();
      if (p.isEmpty()) continue;
      try {
        out.add(MerchantPermission.valueOf(p.toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ignored) {
        // skip unknown (e.g. a permission removed in a later release)
      }
    }
    return out;
  }

  /**
   * Strict parse for owner input: every name must be known.
   *
   * @throws IllegalArgumentException {@code invalid_permission} for an unknown name
   */
  public static Set<MerchantPermission> parseStrict(Collection<String> names) {
    Set<MerchantPermission> out = EnumSet.noneOf(MerchantPermission.class);
    if (names == null) return out;
    for (String n : names) {
      if (n == null || n.isBlank()) continue;
      try {
        out.add(MerchantPermission.valueOf(n.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("invalid_permission");
      }
    }
    return out;
  }

  /** CSV in declaration order (empty string for an empty set — never NULL). */
  public static String toCsv(Collection<MerchantPermission> perms) {
    return String.join(",", names(perms));
  }

  /** Names in declaration order. */
  public static List<String> names(Collection<MerchantPermission> perms) {
    return Arrays.stream(values()).filter(perms::contains).map(Enum::name).toList();
  }
}
