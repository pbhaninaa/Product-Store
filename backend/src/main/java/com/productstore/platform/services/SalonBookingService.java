package com.productstore.platform.services;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.SalonStaffAvailabilityEntity;
import com.productstore.platform.entities.SalonStaffEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SalonBookingService {
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Johannesburg");
  private static final int SLOT_STEP_MINUTES = 15;

  private final SalonServiceRepository services;
  private final SalonStaffRepository staff;
  private final SalonStaffAvailabilityRepository availability;
  private final SalonBookingRepository bookings;
  private final ShopOpeningHoursService shopOpeningHours;
  private final ShopSettingsRepository shopSettings;
  private final EftProofDocumentAnalyzer eftProofAnalyzer;
  private final String uploadsDir;
  private final String publicBaseUrl;

  public SalonBookingService(
      SalonServiceRepository services,
      SalonStaffRepository staff,
      SalonStaffAvailabilityRepository availability,
      SalonBookingRepository bookings,
      ShopOpeningHoursService shopOpeningHours,
      ShopSettingsRepository shopSettings,
      EftProofDocumentAnalyzer eftProofAnalyzer,
      @Value("${app.uploads.dir:./data/uploads}") String uploadsDir,
      @Value("${app.public-base-url:http://localhost:8080}") String publicBaseUrl) {
    this.services = services;
    this.staff = staff;
    this.availability = availability;
    this.bookings = bookings;
    this.shopOpeningHours = shopOpeningHours;
    this.shopSettings = shopSettings;
    this.eftProofAnalyzer = eftProofAnalyzer;
    this.uploadsDir = uploadsDir;
    this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
  }

  public record Slot(Instant startAt, Instant endAt, int capacity, int booked) {}

  public record CreatedBooking(
      UUID bookingId,
      String paymentMethod,
      boolean needsEftProof,
      String paymentReferenceHint,
      String bookingStatus,
      String cashPaymentCode) {}

  public record AdminBookingRow(
      String id,
      String serviceId,
      String serviceName,
      String customerName,
      String customerPhone,
      String customerEmail,
      Instant startAt,
      Instant endAt,
      String status,
      Instant createdAt,
      String clientPaymentMethod,
      String paymentVerificationState,
      String paymentReferenceDeclared,
      String paymentProofUrl,
      String cashPaymentCode) {}

  public List<SalonServiceEntity> listActiveServices(UUID tenantId) {
    return services.findActiveByTenant(tenantId);
  }

  public List<Slot> getAvailableSlots(UUID tenantId, UUID serviceId, LocalDate date) {
    if (serviceId == null) throw new IllegalArgumentException("invalid_service");
    if (date == null) throw new IllegalArgumentException("invalid_date");

    SalonServiceEntity svc =
        services
            .findByIdAndTenantId(serviceId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("service_not_found"));
    if (svc.active == null || !svc.active) throw new IllegalArgumentException("service_not_found");
    int durationMin = svc.durationMinutes == null ? 0 : svc.durationMinutes;
    if (durationMin < 5 || durationMin > 8 * 60) throw new IllegalArgumentException("invalid_duration");

    List<SalonStaffEntity> activeStaff = staff.findActiveByTenant(tenantId);
    if (activeStaff.isEmpty()) return List.of();

    List<UUID> staffIds = activeStaff.stream().map(s -> s.id).toList();
    int dow = date.getDayOfWeek().getValue(); // 1..7
    List<SalonStaffAvailabilityEntity> windows =
        availability.findForStaffOnDay(tenantId, staffIds, dow);

    if (windows.isEmpty()) return List.of();

    // Build the working day bounds (min start to max end across all staff).
    LocalTime minStart =
        windows.stream().map(w -> w.startTime).min(Comparator.naturalOrder()).orElse(LocalTime.of(9, 0));
    LocalTime maxEnd =
        windows.stream().map(w -> w.endTime).max(Comparator.naturalOrder()).orElse(LocalTime.of(17, 0));

    Optional<LocalTime[]> clipped =
        shopOpeningHours.intersectStaffDayBounds(tenantId, dow, minStart, maxEnd);
    if (clipped.isEmpty()) {
      return List.of();
    }
    LocalTime slotMin = clipped.get()[0];
    LocalTime slotMax = clipped.get()[1];

    ZonedDateTime dayStart = ZonedDateTime.of(date, slotMin, DEFAULT_ZONE);
    ZonedDateTime dayEnd = ZonedDateTime.of(date, slotMax, DEFAULT_ZONE);
    if (!dayEnd.isAfter(dayStart)) return List.of();

    // Fetch overlapping bookings for the whole day range.
    Instant rangeStart = dayStart.toInstant();
    Instant rangeEnd = dayEnd.toInstant();
    List<SalonBookingEntity> existing =
        bookings.findOverlapping(
            tenantId,
            rangeStart,
            rangeEnd,
            List.of(SalonBookingEntity.Status.pending, SalonBookingEntity.Status.confirmed));

    List<Slot> result = new ArrayList<>();
    for (ZonedDateTime t = dayStart; !t.plusMinutes(durationMin).isAfter(dayEnd); t = t.plusMinutes(SLOT_STEP_MINUTES)) {
      Instant startAt = t.toInstant();
      Instant endAt = t.plusMinutes(durationMin).toInstant();

      int capacity = countStaffAvailableForWindow(windows, staffIds, date, t.toLocalTime(), t.toLocalTime().plusMinutes(durationMin));
      if (capacity <= 0) continue;

      int bookedCount = (int) existing.stream().filter(b -> overlaps(b.startAt, b.endAt, startAt, endAt)).count();
      if (bookedCount < capacity) {
        result.add(new Slot(startAt, endAt, capacity, bookedCount));
      }
    }
    return result;
  }

  @Transactional
  public CreatedBooking createBooking(
      UUID tenantId,
      UUID serviceId,
      String customerName,
      String customerPhone,
      String customerEmail,
      Instant startAt,
      String paymentMethodRaw) {
    if (serviceId == null) throw new IllegalArgumentException("invalid_service");
    String name = safeTrim(customerName);
    String phone = safeTrim(customerPhone);
    String email = safeTrim(customerEmail).toLowerCase();
    if (name.length() < 2) throw new IllegalArgumentException("invalid_name");
    if (phone.length() < 7) throw new IllegalArgumentException("invalid_phone");
    if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("invalid_email");
    if (startAt == null) throw new IllegalArgumentException("invalid_start");

    ShopSettingsEntity shop = shopSettings.findByTenantId(tenantId).orElse(null);
    boolean shopEft = acceptEft(shop);
    boolean shopCash = acceptCash(shop);
    if (!shopEft && !shopCash) {
      throw new IllegalArgumentException("payment_not_configured");
    }
    SalonBookingEntity.ClientPaymentMethod pm =
        resolveClientPaymentMethod(paymentMethodRaw, shopEft, shopCash);

    SalonServiceEntity svc =
        services
            .findByIdAndTenantId(serviceId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("service_not_found"));
    if (svc.active == null || !svc.active) throw new IllegalArgumentException("service_not_found");
    int durationMin = svc.durationMinutes == null ? 0 : svc.durationMinutes;
    if (durationMin < 5 || durationMin > 8 * 60) throw new IllegalArgumentException("invalid_duration");

    Instant endAt = startAt.plusSeconds(durationMin * 60L);

    // Determine capacity for this window and ensure we don't exceed it.
    LocalDate date = LocalDateTime.ofInstant(startAt, DEFAULT_ZONE).toLocalDate();
    int dow = date.getDayOfWeek().getValue();
    List<SalonStaffEntity> activeStaff = staff.findActiveByTenant(tenantId);
    if (activeStaff.isEmpty()) throw new IllegalArgumentException("no_staff");
    List<UUID> staffIds = activeStaff.stream().map(s -> s.id).toList();
    List<SalonStaffAvailabilityEntity> windows =
        availability.findForStaffOnDay(tenantId, staffIds, dow);
    if (windows.isEmpty()) throw new IllegalArgumentException("no_availability");

    LocalTime localStart = LocalDateTime.ofInstant(startAt, DEFAULT_ZONE).toLocalTime();
    LocalTime localEnd = LocalDateTime.ofInstant(endAt, DEFAULT_ZONE).toLocalTime();

    LocalTime staffMin =
        windows.stream().map(w -> w.startTime).min(Comparator.naturalOrder()).orElse(LocalTime.of(9, 0));
    LocalTime staffMax =
        windows.stream().map(w -> w.endTime).max(Comparator.naturalOrder()).orElse(LocalTime.of(17, 0));
    Optional<LocalTime[]> clipped =
        shopOpeningHours.intersectStaffDayBounds(tenantId, dow, staffMin, staffMax);
    if (clipped.isEmpty()) {
      throw new IllegalArgumentException("slot_unavailable");
    }
    LocalTime slotMin = clipped.get()[0];
    LocalTime slotMax = clipped.get()[1];
    if (localStart.isBefore(slotMin) || localEnd.isAfter(slotMax)) {
      throw new IllegalArgumentException("slot_unavailable");
    }

    int capacity = countStaffAvailableForWindow(windows, staffIds, date, localStart, localEnd);
    if (capacity <= 0) throw new IllegalArgumentException("slot_unavailable");

    List<SalonBookingEntity> existing =
        bookings.findOverlapping(
            tenantId,
            startAt,
            endAt,
            List.of(SalonBookingEntity.Status.pending, SalonBookingEntity.Status.confirmed));
    if (existing.size() >= capacity) throw new IllegalArgumentException("slot_full");

    UUID bookingId = UUID.randomUUID();
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = bookingId;
    b.tenantId = tenantId;
    b.serviceId = serviceId;
    b.staffId = null;
    b.customerName = name;
    b.customerPhone = phone;
    b.customerEmail = email;
    b.startAt = startAt;
    b.endAt = endAt;
    b.clientPaymentMethod = pm;
    b.paymentProofPath = null;
    b.paymentReferenceDeclared = null;
    String cashCode = null;
    if (pm == SalonBookingEntity.ClientPaymentMethod.cash_store) {
      b.status = SalonBookingEntity.Status.pending;
      b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.not_applicable;
      b.cashPaymentCode = generateCashPaymentCode();
      cashCode = b.cashPaymentCode;
    } else {
      b.status = SalonBookingEntity.Status.pending;
      b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.awaiting_proof;
      b.cashPaymentCode = null;
    }
    b.createdAt = Instant.now();
    bookings.save(b);

    String hint = bookingId.toString();
    boolean needs = pm == SalonBookingEntity.ClientPaymentMethod.eft;
    return new CreatedBooking(
        bookingId,
        pm.name(),
        needs,
        hint,
        b.status.name(),
        cashCode == null ? "" : cashCode);
  }

  @Transactional
  public boolean verifyBookingCashPayment(UUID tenantId, UUID bookingId, String code) {
    if (bookingId == null) return false;
    SalonBookingEntity b = bookings.findById(bookingId).orElse(null);
    if (b == null || !b.tenantId.equals(tenantId)) return false;
    if (b.clientPaymentMethod != SalonBookingEntity.ClientPaymentMethod.cash_store) return false;
    if (b.status != SalonBookingEntity.Status.pending) return false;
    if (b.cashPaymentCode == null || b.cashPaymentCode.isBlank()) return false;
    if (!cashCodesMatch(b.cashPaymentCode, code)) return false;
    b.status = SalonBookingEntity.Status.confirmed;
    bookings.save(b);
    return true;
  }

  private static String generateCashPaymentCode() {
    int n = 100_000 + new SecureRandom().nextInt(900_000);
    return String.valueOf(n);
  }

  private static String normalizeCashCode(String s) {
    return safeTrim(s).replaceAll("\\s+", "");
  }

  private static boolean cashCodesMatch(String stored, String provided) {
    String a = normalizeCashCode(stored);
    String b = normalizeCashCode(provided);
    if (a.isEmpty() || b.isEmpty()) return false;
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  @Transactional
  public java.util.Map<String, Object> submitEftProof(
      UUID tenantId, UUID bookingId, String customerEmail, String bankReference, MultipartFile proofFile)
      throws Exception {
    if (bookingId == null) throw new IllegalArgumentException("invalid_booking");
    if (proofFile == null || proofFile.isEmpty()) throw new IllegalArgumentException("proof_required");
    String email = safeTrim(customerEmail).toLowerCase();
    if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException("invalid_email");
    String ref = safeTrim(bankReference);
    if (ref.length() < 3) throw new IllegalArgumentException("invalid_reference");

    SalonBookingEntity b =
        bookings.findById(bookingId).orElseThrow(() -> new IllegalArgumentException("not_found"));
    if (!b.tenantId.equals(tenantId)) throw new IllegalArgumentException("forbidden");
    if (!email.equalsIgnoreCase(safeTrim(b.customerEmail))) throw new IllegalArgumentException("email_mismatch");
    if (b.clientPaymentMethod != SalonBookingEntity.ClientPaymentMethod.eft) {
      throw new IllegalArgumentException("not_eft_booking");
    }
    if (b.paymentVerificationState != SalonBookingEntity.PaymentVerificationState.awaiting_proof) {
      throw new IllegalArgumentException("proof_not_expected");
    }
    if (b.paymentProofPath != null && !b.paymentProofPath.isBlank()) {
      throw new IllegalArgumentException("proof_already_submitted");
    }

    byte[] payload = proofFile.getBytes();
    String ext = eftProofAnalyzer.resolveProofUploadExtension(proofFile.getOriginalFilename(), payload);
    String rel = storeBookingProofBytes(tenantId, bookingId, payload, ext);
    b.paymentProofPath = rel;
    b.paymentReferenceDeclared = ref;

    SalonServiceEntity svc =
        services
            .findByIdAndTenantId(b.serviceId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("service_not_found"));
    BigDecimal expected =
        svc.priceZar == null ? BigDecimal.ZERO : svc.priceZar.setScale(2, RoundingMode.HALF_UP);

    boolean autoOk;
    String autoMode;
    if ("pdf".equals(ext)) {
      autoOk =
          eftProofAnalyzer.verifyPdfAmountAndRecentDate(
              payload, expected, EftProofDocumentAnalyzer.DEFAULT_ZONE);
      autoMode = "pdf_amount_and_date";
    } else {
      autoOk = eftReferenceMatchesBooking(ref, bookingId);
      autoMode = "image_reference";
    }
    if (autoOk) {
      b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.auto_verified;
      b.status = SalonBookingEntity.Status.confirmed;
    } else {
      b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.manual_pending;
      b.status = SalonBookingEntity.Status.pending;
    }
    bookings.save(b);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("paymentVerificationState", b.paymentVerificationState.name());
    out.put("bookingStatus", b.status.name());
    out.put("autoVerified", autoOk);
    out.put("manualVerificationRequired", !autoOk);
    out.put("proofType", ext);
    out.put("autoVerifyMode", autoMode);
    return out;
  }

  @Transactional
  public void approveManualEftPayment(UUID tenantId, UUID bookingId) {
    SalonBookingEntity b =
        bookings.findById(bookingId).orElseThrow(() -> new IllegalArgumentException("not_found"));
    if (!b.tenantId.equals(tenantId)) throw new IllegalArgumentException("forbidden");
    if (b.clientPaymentMethod != SalonBookingEntity.ClientPaymentMethod.eft) {
      throw new IllegalArgumentException("not_eft_booking");
    }
    if (b.paymentVerificationState != SalonBookingEntity.PaymentVerificationState.manual_pending) {
      throw new IllegalArgumentException("not_pending_manual_review");
    }
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.manual_approved;
    b.status = SalonBookingEntity.Status.confirmed;
    bookings.save(b);
  }

  @Transactional
  public void rejectManualEftPayment(UUID tenantId, UUID bookingId) {
    SalonBookingEntity b =
        bookings.findById(bookingId).orElseThrow(() -> new IllegalArgumentException("not_found"));
    if (!b.tenantId.equals(tenantId)) throw new IllegalArgumentException("forbidden");
    if (b.clientPaymentMethod != SalonBookingEntity.ClientPaymentMethod.eft) {
      throw new IllegalArgumentException("not_eft_booking");
    }
    if (b.paymentVerificationState != SalonBookingEntity.PaymentVerificationState.manual_pending) {
      throw new IllegalArgumentException("not_pending_manual_review");
    }
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.manual_rejected;
    b.status = SalonBookingEntity.Status.cancelled;
    bookings.save(b);
  }

  private String storeBookingProofBytes(UUID tenantId, UUID bookingId, byte[] payload, String ext)
      throws Exception {
    if (!"pdf".equals(ext) && !EftProofDocumentAnalyzer.PROOF_IMAGE_EXTENSIONS.contains(ext)) {
      throw new IllegalArgumentException("unsupported_proof_type");
    }
    Path dir = Paths.get(uploadsDir, "salon-bookings", tenantId.toString()).toAbsolutePath().normalize();
    Files.createDirectories(dir);
    Path target = dir.resolve(bookingId + "." + ext);
    Files.write(target, payload);
    return "salon-bookings/" + tenantId + "/" + bookingId + "." + ext;
  }

  private static String extension(String filename) {
    int i = filename.lastIndexOf('.');
    if (i < 0 || i >= filename.length() - 1) return "";
    return filename.substring(i + 1).toLowerCase(Locale.ROOT);
  }

  private static boolean eftReferenceMatchesBooking(String declared, UUID bookingId) {
    String n = normalizeRef(declared);
    if (n.isEmpty()) return false;
    String compact = bookingId.toString().replace("-", "").toLowerCase(Locale.ROOT);
    String dashed = bookingId.toString().toLowerCase(Locale.ROOT);
    return n.contains(compact) || n.contains(dashed.replace("-", ""));
  }

  private static String normalizeRef(String s) {
    if (s == null) return "";
    return s.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
  }

  private static boolean acceptEft(ShopSettingsEntity s) {
    return s == null || s.acceptCustomerEft == null || Boolean.TRUE.equals(s.acceptCustomerEft);
  }

  private static boolean acceptCash(ShopSettingsEntity s) {
    return s == null || s.acceptCustomerCash == null || Boolean.TRUE.equals(s.acceptCustomerCash);
  }

  private static SalonBookingEntity.ClientPaymentMethod resolveClientPaymentMethod(
      String paymentMethodRaw, boolean shopEft, boolean shopCash) {
    String raw = safeTrim(paymentMethodRaw).toLowerCase(Locale.ROOT);
    if (shopEft && !shopCash) {
      return SalonBookingEntity.ClientPaymentMethod.eft;
    }
    if (!shopEft && shopCash) {
      return SalonBookingEntity.ClientPaymentMethod.cash_store;
    }
    if (raw.isEmpty()) {
      throw new IllegalArgumentException("payment_method_required");
    }
    if (raw.equals("eft")) {
      if (!shopEft) throw new IllegalArgumentException("eft_not_accepted");
      return SalonBookingEntity.ClientPaymentMethod.eft;
    }
    if (raw.equals("cash_store") || raw.equals("cash")) {
      if (!shopCash) throw new IllegalArgumentException("cash_not_accepted");
      return SalonBookingEntity.ClientPaymentMethod.cash_store;
    }
    throw new IllegalArgumentException("invalid_payment_method");
  }

  public List<AdminBookingRow> listAdminBookings(UUID tenantId) {
    return bookings.findByTenantIdOrderByStartAtDesc(tenantId).stream()
        .map(
            b -> {
              String svcName =
                  services
                      .findByIdAndTenantId(b.serviceId, tenantId)
                      .map(s -> s.name == null ? "" : s.name)
                      .orElse("—");
              String pm =
                  b.clientPaymentMethod == null ? "" : b.clientPaymentMethod.name();
              String pvs =
                  b.paymentVerificationState == null ? "" : b.paymentVerificationState.name();
              String pref = b.paymentReferenceDeclared == null ? "" : b.paymentReferenceDeclared;
              String proofUrl = proofPublicUrl(b.paymentProofPath);
              String cash =
                  b.cashPaymentCode == null || b.cashPaymentCode.isBlank() ? "" : b.cashPaymentCode;
              return new AdminBookingRow(
                  b.id.toString(),
                  b.serviceId.toString(),
                  svcName,
                  b.customerName,
                  b.customerPhone,
                  b.customerEmail,
                  b.startAt,
                  b.endAt,
                  b.status.name(),
                  b.createdAt,
                  pm,
                  pvs,
                  pref,
                  proofUrl,
                  cash);
            })
        .toList();
  }

  private String proofPublicUrl(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return "";
    String p = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
    return publicBaseUrl + "/uploads" + p;
  }

  /**
   * Hard-deletes a booking row. Allowed only when the appointment is not in a paid/locked state ({@code confirmed}).
   * Pending EFT bookings and cancelled rows may be removed to clear the list.
   */
  @Transactional
  public boolean deleteBookingPermanentlyIfNotConfirmed(UUID tenantId, UUID bookingId) {
    if (bookingId == null) return false;
    SalonBookingEntity b = bookings.findById(bookingId).orElse(null);
    if (b == null || !b.tenantId.equals(tenantId)) return false;
    if (b.status == SalonBookingEntity.Status.confirmed) return false;

    tryDeleteBookingProofUpload(b.paymentProofPath);
    bookings.delete(b);
    return true;
  }

  private void tryDeleteBookingProofUpload(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return;
    try {
      Path base = Paths.get(uploadsDir).toAbsolutePath().normalize();
      Path p = Paths.get(uploadsDir, relativePath).toAbsolutePath().normalize();
      if (!p.startsWith(base)) return;
      Files.deleteIfExists(p);
    } catch (Exception ignored) {
      // best-effort cleanup
    }
  }

  @Transactional
  public void updateBookingStatus(UUID tenantId, UUID bookingId, String statusRaw) {
    if (statusRaw == null || statusRaw.isBlank()) throw new IllegalArgumentException("invalid_status");
    SalonBookingEntity.Status newStatus;
    try {
      newStatus = SalonBookingEntity.Status.valueOf(statusRaw.trim().toLowerCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid_status");
    }
    SalonBookingEntity b =
        bookings.findById(bookingId).orElseThrow(() -> new IllegalArgumentException("not_found"));
    if (!b.tenantId.equals(tenantId)) throw new IllegalArgumentException("forbidden");
    if (newStatus == SalonBookingEntity.Status.confirmed && blocksMerchantQuickConfirm(b)) {
      throw new IllegalArgumentException("payment_verification_required");
    }
    assertBookingStatusTransition(b.status, newStatus);
    b.status = newStatus;
    bookings.save(b);
  }

  private static boolean blocksMerchantQuickConfirm(SalonBookingEntity b) {
    if (b.clientPaymentMethod == SalonBookingEntity.ClientPaymentMethod.cash_store
        && b.status == SalonBookingEntity.Status.pending) {
      return true;
    }
    if (b.clientPaymentMethod != SalonBookingEntity.ClientPaymentMethod.eft) {
      return false;
    }
    SalonBookingEntity.PaymentVerificationState s = b.paymentVerificationState;
    if (s == null) {
      return false;
    }
    return s == SalonBookingEntity.PaymentVerificationState.awaiting_proof
        || s == SalonBookingEntity.PaymentVerificationState.manual_pending;
  }

  private static void assertBookingStatusTransition(
      SalonBookingEntity.Status from, SalonBookingEntity.Status to) {
    if (from == to) return;
    if (from == SalonBookingEntity.Status.cancelled) {
      throw new IllegalArgumentException("invalid_status");
    }
    if (to == SalonBookingEntity.Status.pending) {
      throw new IllegalArgumentException("invalid_status");
    }
    if (to == SalonBookingEntity.Status.cancelled) {
      return;
    }
    if (to == SalonBookingEntity.Status.confirmed && from == SalonBookingEntity.Status.pending) {
      return;
    }
    throw new IllegalArgumentException("invalid_status");
  }

  private static String safeTrim(String s) {
    return s == null ? "" : s.trim();
  }

  private static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
    return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
  }

  private static int countStaffAvailableForWindow(
      List<SalonStaffAvailabilityEntity> windows,
      List<UUID> staffIds,
      LocalDate date,
      LocalTime start,
      LocalTime end) {
    int count = 0;
    for (UUID staffId : staffIds) {
      boolean ok =
          windows.stream()
              .filter(w -> w.staffId.equals(staffId))
              .anyMatch(w -> !w.startTime.isAfter(start) && !w.endTime.isBefore(end));
      if (ok) count += 1;
    }
    return count;
  }
}
