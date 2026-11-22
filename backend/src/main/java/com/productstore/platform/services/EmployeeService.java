package com.productstore.platform.services;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.productstore.platform.constants.EmployeePayMethod;
import com.productstore.platform.entities.EmployeeEntity;
import com.productstore.platform.entities.EmployeePayrollJobMarkEntity;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.UserEntity;
import com.productstore.platform.repositories.EmployeePayrollJobMarkRepository;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.repositories.OrderRepository;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {
  public static final String JOB_ORDER = "ORDER";
  public static final String JOB_SALON_BOOKING = "SALON_BOOKING";

  /** My income line statuses: assigned but not payable yet / payable and unpaid / marked paid. */
  public static final String LINE_UPCOMING = "UPCOMING";
  public static final String LINE_OWED = "OWED";
  public static final String LINE_PAID_OUT = "PAID_OUT";

  private static final Comparator<JobLine> NEWEST_FIRST =
      Comparator.comparing((JobLine l) -> l.when, Comparator.nullsLast(Comparator.reverseOrder()));

  private final EmployeeRepository employees;
  private final EmployeePayrollJobMarkRepository payrollMarks;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final OrderRepository orders;
  private final SalonBookingRepository bookings;
  private final PasswordHasher passwordHasher;
  private final InAppNotificationService inAppNotifications;
  private final MerchantSubscriptionService subscriptions;
  private final SalonStaffSyncService salonStaffSync;
  private final SalonStaffRepository salonStaff;

  public EmployeeService(
      EmployeeRepository employees,
      EmployeePayrollJobMarkRepository payrollMarks,
      UserRepository users,
      MembershipRepository memberships,
      OrderRepository orders,
      SalonBookingRepository bookings,
      PasswordHasher passwordHasher,
      InAppNotificationService inAppNotifications,
      MerchantSubscriptionService subscriptions,
      SalonStaffSyncService salonStaffSync,
      SalonStaffRepository salonStaff) {
    this.salonStaff = salonStaff;
    this.employees = employees;
    this.payrollMarks = payrollMarks;
    this.users = users;
    this.memberships = memberships;
    this.orders = orders;
    this.bookings = bookings;
    this.passwordHasher = passwordHasher;
    this.inAppNotifications = inAppNotifications;
    this.subscriptions = subscriptions;
    this.salonStaffSync = salonStaffSync;
  }

  public List<Map<String, Object>> listTeam(UUID tenantId) {
    return employees.findByTenantId(tenantId).stream().map(this::toTeamMember).toList();
  }

  @Transactional
  public Map<String, Object> createEmployee(
      UUID tenantId,
      UUID actorUserId,
      String email,
      String password,
      String displayName,
      String roleLabel,
      String payMethod,
      Double payRate,
      Double bonusPercentage) {
    subscriptions.assertCanAddEmployee(tenantId);
    if (email == null || email.isBlank() || password == null || password.isBlank()) {
      throw new IllegalArgumentException("email_and_password_required");
    }
    String normalizedEmail = email.trim().toLowerCase();
    if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
      throw new IllegalStateException("email_already_exists");
    }
    String method = normalizePayMethod(payMethod);

    UserEntity user = new UserEntity();
    user.id = UUID.randomUUID();
    user.email = normalizedEmail;
    user.passwordHash = passwordHasher.hash(password);
    user.createdAt = Instant.now();
    users.save(user);

    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = user.id;
    m.tenantId = tenantId;
    m.role = Role.MERCHANT_STAFF;
    m.createdAt = Instant.now();
    memberships.save(m);

    EmployeeEntity emp = new EmployeeEntity();
    emp.tenantId = tenantId;
    emp.userProfileId = user.id;
    emp.displayName =
        displayName != null && !displayName.isBlank() ? displayName.trim() : normalizedEmail;
    emp.role = roleLabel != null && !roleLabel.isBlank() ? roleLabel.trim() : "STAFF";
    emp.payMethod = method;
    emp.payRate = payRate;
    emp.bonusPercentage = bonusPercentage;
    emp.isActive = true;
    emp.createdBy = actorUserId != null ? actorUserId.toString() : null;
    employees.save(emp);
    salonStaffSync.upsertFromEmployee(emp);

    return toTeamMember(emp);
  }

  @Transactional
  public Map<String, Object> updateEmployee(
      UUID tenantId,
      UUID employeeId,
      String displayName,
      String roleLabel,
      String payMethod,
      Double payRate,
      Double bonusPercentage,
      Boolean active) {
    EmployeeEntity emp =
        employees
            .findById(employeeId)
            .filter(e -> e.tenantId.equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("employee_not_found"));
    if (displayName != null && !displayName.isBlank()) emp.displayName = displayName.trim();
    if (roleLabel != null && !roleLabel.isBlank()) emp.role = roleLabel.trim();
    if (payMethod != null && !payMethod.isBlank()) emp.payMethod = normalizePayMethod(payMethod);
    if (payRate != null) emp.payRate = payRate;
    if (bonusPercentage != null) emp.bonusPercentage = bonusPercentage;
    if (active != null) emp.isActive = active;
    employees.save(emp);
    if (active != null) syncStaffAccess(emp);
    salonStaffSync.upsertFromEmployee(emp);
    return toTeamMember(emp);
  }

  /**
   * Store access is granted by the MERCHANT_STAFF membership, not by {@code isActive}, so the two must
   * move together: an inactive employee loses the membership (and every open session) immediately.
   */
  private void syncStaffAccess(EmployeeEntity emp) {
    if (emp.userProfileId == null) return;
    List<MembershipEntity> staff =
        memberships.findAllByUserId(emp.userProfileId).stream()
            .filter(m -> emp.tenantId.equals(m.tenantId) && m.role == Role.MERCHANT_STAFF)
            .toList();
    if (emp.isActive) {
      if (staff.isEmpty()) {
        MembershipEntity m = new MembershipEntity();
        m.id = UUID.randomUUID();
        m.userId = emp.userProfileId;
        m.tenantId = emp.tenantId;
        m.role = Role.MERCHANT_STAFF;
        m.createdAt = Instant.now();
        memberships.save(m);
      }
      return;
    }
    if (staff.isEmpty()) return;
    memberships.deleteAll(staff);
    users
        .findById(emp.userProfileId)
        .ifPresent(
            u -> {
              u.revokeTokens();
              users.save(u);
            });
  }

  @Transactional
  public void deactivateEmployee(UUID tenantId, UUID employeeId) {
    EmployeeEntity emp =
        employees
            .findById(employeeId)
            .filter(e -> e.tenantId.equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("employee_not_found"));
    emp.isActive = false;
    employees.save(emp);
    syncStaffAccess(emp);
    salonStaffSync.upsertFromEmployee(emp);
  }

  public Map<String, Object> paymentCalculations(
      UUID tenantId, LocalDate startDate, LocalDate endDate) {
    subscriptions.assertCanUsePayroll(tenantId);
    requireBothOrNeither(startDate, endDate);
    List<Map<String, Object>> calcs = new ArrayList<>();
    for (EmployeeEntity emp : employees.findByTenantIdAndIsActive(tenantId, true)) {
      List<JobLine> lines = buildJobLines(tenantId, emp, startDate, endDate);
      applyMarks(tenantId, emp.id, lines);
      double pending = sumPending(emp, lines);
      double units = unitsForUnpaid(emp, lines);
      int jobCount = (int) lines.stream().filter(l -> !l.employerPaid).count();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("employeeId", emp.id.toString());
      row.put("displayName", nz(emp.displayName));
      row.put("email", userEmail(emp.userProfileId));
      row.put("role", nz(emp.role));
      row.put("payMethod", nz(emp.payMethod));
      row.put("payRate", emp.payRate);
      row.put("bonusPercentage", emp.bonusPercentage);
      row.put("pendingExpected", round2(pending));
      row.put("units", round2(units));
      row.put("jobCount", jobCount);
      row.put("lines", lines.stream().map(this::lineToMap).toList());
      calcs.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("payPeriodStart", startDate != null ? startDate.toString() : null);
    out.put("payPeriodEnd", endDate != null ? endDate.toString() : null);
    out.put("calculations", calcs);
    return out;
  }

  public Map<String, Object> myExpectedIncome(
      UUID tenantId, UUID userId, LocalDate startDate, LocalDate endDate) {
    subscriptions.assertCanUsePayroll(tenantId);
    requireBothOrNeither(startDate, endDate);
    var empOpt =
        employees
            .findByUserProfileId(userId)
            .filter(e -> e.tenantId.equals(tenantId) && e.isActive);
    if (empOpt.isEmpty()) {
      // User is not an employee (e.g., owner-only account), return empty result
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("employeeId", null);
      out.put("displayName", "");
      out.put("payMethod", "");
      out.put("payRate", null);
      out.put("bonusPercentage", null);
      out.put("payPeriodStart", startDate != null ? startDate.toString() : null);
      out.put("payPeriodEnd", endDate != null ? endDate.toString() : null);
      out.put("pendingExpected", 0.0);
      out.put("owedTotal", 0.0);
      out.put("owedCount", 0);
      out.put("upcomingTotal", 0.0);
      out.put("upcomingCount", 0);
      out.put("paidOutTotal", 0.0);
      out.put("lines", List.of());
      return out;
    }
    EmployeeEntity emp = empOpt.get();
    // Owed + Paid out: exactly the lines owner payroll sees (same builder, same marks).
    List<JobLine> payrollLines = buildJobLines(tenantId, emp, startDate, endDate);
    applyMarks(tenantId, emp.id, payrollLines);
    List<JobLine> owed = payrollLines.stream().filter(l -> !l.employerPaid).toList();
    List<JobLine> paidOut = payrollLines.stream().filter(l -> l.employerPaid).toList();
    // Upcoming: assigned to this employee but not payable yet. Display-only; never fed to payroll.
    Set<UUID> payrollJobIds = payrollLines.stream().map(l -> l.jobId).collect(Collectors.toSet());
    List<JobLine> upcoming =
        buildUpcomingLines(tenantId, emp, startDate, endDate, payrollJobIds, Instant.now());

    double owedTotal = sumPending(emp, payrollLines);
    List<JobLine> owedPlusUpcoming = new ArrayList<>(owed);
    owedPlusUpcoming.addAll(upcoming);
    // What the upcoming work adds on top of what is already owed (a salary or a day already counted is not
    // counted twice).
    double upcomingTotal = Math.max(0.0, payFor(emp, owedPlusUpcoming) - payFor(emp, owed));

    List<Map<String, Object>> outLines = new ArrayList<>();
    upcoming.stream()
        .sorted(NEWEST_FIRST)
        .forEach(l -> outLines.add(myIncomeLineToMap(l, LINE_UPCOMING)));
    payrollLines.stream()
        .sorted(NEWEST_FIRST)
        .forEach(
            l -> outLines.add(myIncomeLineToMap(l, l.employerPaid ? LINE_PAID_OUT : LINE_OWED)));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("employeeId", emp.id.toString());
    out.put("displayName", nz(emp.displayName));
    out.put("payMethod", nz(emp.payMethod));
    out.put("payRate", emp.payRate);
    out.put("bonusPercentage", emp.bonusPercentage);
    out.put("payPeriodStart", startDate != null ? startDate.toString() : null);
    out.put("payPeriodEnd", endDate != null ? endDate.toString() : null);
    out.put("pendingExpected", round2(owedTotal)); // kept for older clients; equals owedTotal
    out.put("owedTotal", round2(owedTotal));
    out.put("owedCount", owed.size());
    out.put("upcomingTotal", round2(upcomingTotal));
    out.put("upcomingCount", upcoming.size());
    out.put("paidOutTotal", round2(payFor(emp, paidOut)));
    out.put("lines", outLines);
    return out;
  }

  /**
   * Bookings assigned to {@code emp} that owner payroll does not (yet) treat as payable — shown to the
   * employee as "Upcoming" on My income only. Payroll ({@link #buildJobLines}, {@link #jobAttributed}) pays a
   * booking once it is credited to the employee ({@code completedByEmployeeId}) and confirmed / in progress /
   * completed; those are "Owed" (or "Paid out" once marked), never Upcoming. A booking is Upcoming when:
   *
   * <ul>
   *   <li>it is {@code pending} (awaiting payment / proof) and either credited to {@code emp} or booked with
   *       the salon staff member linked to {@code emp} and credited to nobody — unless its proof was rejected
   *       or its slot has already ended (a stale, never-paid request is not work coming up);
   *   <li>it is {@code confirmed} / {@code in_progress}, booked with the linked salon staff member, and not yet
   *       credited to anyone (it becomes Owed once credited on start/complete or by the owner's Assign).
   * </ul>
   *
   * {@code cancelled} never counts; {@code completed} is finished work, so it is Owed once credited. A booking
   * credited to another employee is theirs. Orders have no assignment before payment, so they are never
   * Upcoming. The date filter uses the appointment start; no range means all such bookings.
   */
  private List<JobLine> buildUpcomingLines(
      UUID tenantId,
      EmployeeEntity emp,
      LocalDate start,
      LocalDate end,
      Set<UUID> alreadyOnPayroll,
      Instant now) {
    Map<UUID, SalonBookingEntity> candidates = new LinkedHashMap<>();
    for (SalonBookingEntity b :
        bookings.findByTenantIdAndCompletedByEmployeeIdAndStatusIn(
            tenantId, emp.id, List.of(SalonBookingEntity.Status.pending))) {
      candidates.put(b.id, b);
    }
    salonStaff
        .findByTenantIdAndEmployeeId(tenantId, emp.id)
        .ifPresent(
            staff ->
                bookings
                    .findByTenantIdAndStaffIdAndCompletedByEmployeeIdIsNullAndStatusIn(
                        tenantId,
                        staff.id,
                        List.of(
                            SalonBookingEntity.Status.pending,
                            SalonBookingEntity.Status.confirmed,
                            SalonBookingEntity.Status.in_progress))
                    .forEach(b -> candidates.putIfAbsent(b.id, b)));
    List<JobLine> lines = new ArrayList<>();
    for (SalonBookingEntity b : candidates.values()) {
      if (alreadyOnPayroll.contains(b.id)) continue;
      if (b.status == SalonBookingEntity.Status.pending) {
        if (b.paymentVerificationState == SalonBookingEntity.PaymentVerificationState.manual_rejected) {
          continue;
        }
        Instant slotEnd = b.endAt != null ? b.endAt : b.startAt;
        if (slotEnd != null && slotEnd.isBefore(now)) continue;
      }
      if (!inPeriod(b.startAt, start, end)) continue;
      lines.add(bookingLine(emp, b, b.startAt));
    }
    return lines;
  }

  @Transactional
  public void markJobPaid(
      UUID tenantId,
      UUID actorUserId,
      UUID employeeId,
      UUID jobId,
      String jobTypeRaw,
      Boolean includeBonus) {
    subscriptions.assertCanUsePayroll(tenantId);
    String jobType = normalizeJobType(jobTypeRaw);
    EmployeeEntity emp = requireActiveEmployee(tenantId, employeeId);
    if (!jobAttributed(tenantId, emp.id, jobId, jobType)) {
      throw new IllegalStateException("job_not_attributed");
    }
    if (payrollMarks
        .findByTenantIdAndEmployeeRecordIdAndJobIdAndJobType(tenantId, emp.id, jobId, jobType)
        .isPresent()) {
      return;
    }
    EmployeePayrollJobMarkEntity mark = new EmployeePayrollJobMarkEntity();
    mark.tenantId = tenantId;
    mark.employeeRecordId = emp.id;
    mark.jobId = jobId;
    mark.jobType = jobType;
    mark.markedByUsername = actorUserId != null ? actorUserId.toString() : null;
    mark.includeBonus = includeBonus;
    payrollMarks.save(mark);
    inAppNotifications.notifyUser(
        emp.userProfileId,
        tenantId,
        "Payroll paid",
        "A job was marked paid by your employer.",
        "PAYROLL_PAID",
        jobType,
        jobId.toString());
  }

  @Transactional
  public void unmarkJobPaid(UUID tenantId, UUID employeeId, UUID jobId, String jobTypeRaw) {
    String jobType = normalizeJobType(jobTypeRaw);
    requireActiveEmployee(tenantId, employeeId);
    payrollMarks.deleteByTenantIdAndEmployeeRecordIdAndJobIdAndJobType(
        tenantId, employeeId, jobId, jobType);
  }

  @Transactional
  public int payAllPending(
      UUID tenantId, UUID actorUserId, UUID employeeId, Boolean includeBonus) {
    EmployeeEntity emp = requireActiveEmployee(tenantId, employeeId);
    List<JobLine> lines = buildJobLines(tenantId, emp, null, null);
    applyMarks(tenantId, emp.id, lines);
    int n = 0;
    for (JobLine line : lines) {
      if (line.employerPaid) continue;
      markJobPaid(tenantId, actorUserId, employeeId, line.jobId, line.jobType, includeBonus);
      n++;
    }
    return n;
  }

  @Transactional
  public void assignCompletedWork(
      UUID tenantId, String jobTypeRaw, UUID jobId, UUID employeeId) {
    String jobType = normalizeJobType(jobTypeRaw);
    if (employeeId != null) {
      requireActiveEmployee(tenantId, employeeId);
    }
    if (JOB_ORDER.equals(jobType)) {
      OrderEntity o = orders.findOneByTenantAndId(tenantId, jobId);
      if (o == null) throw new IllegalArgumentException("order_not_found");
      if (o.status != OrderEntity.OrderStatus.paid) {
        throw new IllegalStateException("order_not_paid");
      }
      o.completedByEmployeeId = employeeId;
      if (o.completedAt == null) {
        o.completedAt = o.paymentConfirmedAt != null ? o.paymentConfirmedAt : Instant.now();
      }
      orders.save(o);
      return;
    }
    if (JOB_SALON_BOOKING.equals(jobType)) {
      SalonBookingEntity b = bookings.findOneByTenantAndId(tenantId, jobId);
      if (b == null) throw new IllegalArgumentException("booking_not_found");
      if (b.status != SalonBookingEntity.Status.confirmed
          && b.status != SalonBookingEntity.Status.in_progress
          && b.status != SalonBookingEntity.Status.completed) {
        throw new IllegalStateException("booking_not_confirmed");
      }
      b.completedByEmployeeId = employeeId;
      if (b.completedAt == null) {
        b.completedAt = Instant.now();
      }
      bookings.save(b);
      return;
    }
    throw new IllegalArgumentException("invalid_job_type");
  }

  /**
   * Credits a paid booking to whoever does the work, so it shows under their payroll without the owner
   * having to assign it. Runs after a booking is confirmed (cash code, EFT approval, PayFast) or moved to
   * in progress / completed. Only fills an empty attribution — it never takes a job away from someone the
   * owner already assigned. Picks, in order:
   * <ol>
   *   <li>the staff member the customer booked, when that person is a team employee;</li>
   *   <li>otherwise the signed-in team member who performed the action.</li>
   * </ol>
   * An owner without an employee record is never credited; they can still use Assign.
   */
  @Transactional
  public void creditBookingIfUnattributed(UUID tenantId, UUID bookingId, UUID actingUserId) {
    SalonBookingEntity b = bookings.findOneByTenantAndId(tenantId, bookingId);
    if (b == null || b.completedByEmployeeId != null) return;
    if (b.status != SalonBookingEntity.Status.confirmed
        && b.status != SalonBookingEntity.Status.in_progress
        && b.status != SalonBookingEntity.Status.completed) {
      return;
    }
    UUID employeeId = null;
    if (b.staffId != null) {
      employeeId =
          salonStaff
              .findByIdAndTenantId(b.staffId, tenantId)
              .map(s -> s.employeeId)
              .flatMap(id -> employees.findById(id))
              .filter(e -> e.tenantId.equals(tenantId) && e.isActive)
              .map(e -> e.id)
              .orElse(null);
    }
    if (employeeId == null && actingUserId != null) {
      employeeId =
          employees
              .findByUserProfileId(actingUserId)
              .filter(e -> e.tenantId.equals(tenantId) && e.isActive)
              .map(e -> e.id)
              .orElse(null);
    }
    if (employeeId == null) return;
    b.completedByEmployeeId = employeeId;
    if (b.completedAt == null) b.completedAt = Instant.now();
    bookings.save(b);
  }

  public EmployeeEntity requireActiveEmployee(UUID tenantId, UUID employeeId) {
    return employees
        .findById(employeeId)
        .filter(e -> e.tenantId.equals(tenantId) && e.isActive)
        .orElseThrow(() -> new IllegalArgumentException("employee_not_found"));
  }

  private boolean jobAttributed(UUID tenantId, UUID employeeId, UUID jobId, String jobType) {
    if (JOB_ORDER.equals(jobType)) {
      OrderEntity o = orders.findOneByTenantAndId(tenantId, jobId);
      return o != null
          && o.status == OrderEntity.OrderStatus.paid
          && employeeId.equals(o.completedByEmployeeId);
    }
    if (JOB_SALON_BOOKING.equals(jobType)) {
      return bookings.findConfirmedByTenantAndEmployee(tenantId, employeeId).stream()
          .anyMatch(b -> b.id.equals(jobId));
    }
    return false;
  }

  private List<JobLine> buildJobLines(
      UUID tenantId, EmployeeEntity emp, LocalDate start, LocalDate end) {
    List<JobLine> lines = new ArrayList<>();
    for (OrderEntity o : orders.findPaidByTenantAndEmployee(tenantId, emp.id)) {
      Instant when = o.completedAt != null ? o.completedAt : o.paymentConfirmedAt;
      if (!inPeriod(when, start, end)) continue;
      lines.add(
          new JobLine(
              o.id,
              JOB_ORDER,
              when,
              expectedAmount(emp, 1.0, Duration.ofHours(1)),
              1.0,
              false));
    }
    for (SalonBookingEntity b : bookings.findConfirmedByTenantAndEmployee(tenantId, emp.id)) {
      Instant when = b.completedAt != null ? b.completedAt : b.startAt;
      if (!inPeriod(when, start, end)) continue;
      lines.add(bookingLine(emp, b, when));
    }
    return lines;
  }

  /**
   * Employee pay for one booking (pay method / rate / bonus — not the customer's price, so a booking's
   * stored amount never changes it).
   */
  private JobLine bookingLine(EmployeeEntity emp, SalonBookingEntity b, Instant when) {
    Duration dur =
        (b.startAt != null && b.endAt != null && b.endAt.isAfter(b.startAt))
            ? Duration.between(b.startAt, b.endAt)
            : Duration.ofHours(1);
    double hours = Math.max(1.0, dur.toMinutes() / 60.0);
    return new JobLine(
        b.id, JOB_SALON_BOOKING, when, expectedAmount(emp, hours, dur), unitsForJob(emp, hours), false);
  }

  private double expectedAmount(EmployeeEntity emp, double hoursOrUnits, Duration bookingDur) {
    String method = emp.payMethod == null ? "" : emp.payMethod.trim().toUpperCase();
    double rate = emp.payRate == null ? 0.0 : emp.payRate;
    double base;
    if (EmployeePayMethod.PER_HOUR.name().equals(method)) {
      base = hoursOrUnits * rate;
    } else if (EmployeePayMethod.PER_DAY.name().equals(method)) {
      base = rate;
    } else if (EmployeePayMethod.WEEKLY.name().equals(method)
        || EmployeePayMethod.MONTHLY.name().equals(method)) {
      base = rate;
    } else {
      // PER_SERVICE default
      base = rate;
    }
    double bonusPct = emp.bonusPercentage == null ? 0.0 : emp.bonusPercentage;
    return round2(base * (1.0 + bonusPct / 100.0));
  }

  private double unitsForJob(EmployeeEntity emp, double hours) {
    String method = emp.payMethod == null ? "" : emp.payMethod.trim().toUpperCase();
    if (EmployeePayMethod.PER_HOUR.name().equals(method)) return hours;
    if (EmployeePayMethod.PER_DAY.name().equals(method)) return 1.0;
    if (EmployeePayMethod.WEEKLY.name().equals(method)
        || EmployeePayMethod.MONTHLY.name().equals(method)) return 0.0;
    return 1.0;
  }

  private void applyMarks(UUID tenantId, UUID employeeId, List<JobLine> lines) {
    Set<String> paidKeys =
        payrollMarks.findByTenantIdAndEmployeeRecordId(tenantId, employeeId).stream()
            .map(m -> m.jobType + ":" + m.jobId)
            .collect(Collectors.toSet());
    for (JobLine line : lines) {
      line.employerPaid = paidKeys.contains(line.jobType + ":" + line.jobId);
    }
  }

  private double sumPending(EmployeeEntity emp, List<JobLine> lines) {
    String method = emp.payMethod == null ? "" : emp.payMethod.trim().toUpperCase();
    boolean fixed =
        EmployeePayMethod.WEEKLY.name().equals(method)
            || EmployeePayMethod.MONTHLY.name().equals(method);
    if (fixed) {
      boolean anyPending = lines.stream().anyMatch(l -> !l.employerPaid);
      if (!anyPending) return 0.0;
      double rate = emp.payRate == null ? 0.0 : emp.payRate;
      double bonusPct = emp.bonusPercentage == null ? 0.0 : emp.bonusPercentage;
      return round2(rate * (1.0 + bonusPct / 100.0));
    }
    if (EmployeePayMethod.PER_DAY.name().equals(method)) {
      Set<LocalDate> unpaidDays = new HashSet<>();
      for (JobLine l : lines) {
        if (l.employerPaid || l.when == null) continue;
        unpaidDays.add(l.when.atZone(ZoneOffset.UTC).toLocalDate());
      }
      double rate = emp.payRate == null ? 0.0 : emp.payRate;
      double bonusPct = emp.bonusPercentage == null ? 0.0 : emp.bonusPercentage;
      return round2(unpaidDays.size() * rate * (1.0 + bonusPct / 100.0));
    }
    double sum = 0.0;
    for (JobLine l : lines) {
      if (!l.employerPaid) sum += l.expectedAmount;
    }
    return round2(sum);
  }

  /**
   * Pay for {@code lines} as if none were marked paid, using the same rules as {@link #sumPending} (a
   * weekly/monthly salary once, a per-day rate once per distinct day, otherwise the sum of the lines).
   */
  private double payFor(EmployeeEntity emp, List<JobLine> lines) {
    List<JobLine> unpaid =
        lines.stream()
            .map(l -> new JobLine(l.jobId, l.jobType, l.when, l.expectedAmount, l.units, false))
            .toList();
    return sumPending(emp, unpaid);
  }

  private double unitsForUnpaid(EmployeeEntity emp, List<JobLine> lines) {
    String method = emp.payMethod == null ? "" : emp.payMethod.trim().toUpperCase();
    if (EmployeePayMethod.PER_DAY.name().equals(method)) {
      Set<LocalDate> unpaidDays = new HashSet<>();
      for (JobLine l : lines) {
        if (l.employerPaid || l.when == null) continue;
        unpaidDays.add(l.when.atZone(ZoneOffset.UTC).toLocalDate());
      }
      return unpaidDays.size();
    }
    return lines.stream().filter(l -> !l.employerPaid).mapToDouble(l -> l.units).sum();
  }

  private Map<String, Object> lineToMap(JobLine line) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("jobId", line.jobId.toString());
    m.put("jobType", line.jobType);
    m.put("when", line.when != null ? line.when.toString() : null);
    m.put("expectedAmount", line.expectedAmount);
    m.put("units", line.units);
    m.put("employerPaid", line.employerPaid);
    return m;
  }

  /** A My income line: the payroll line shape plus {@code status} (UPCOMING | OWED | PAID_OUT). */
  private Map<String, Object> myIncomeLineToMap(JobLine line, String status) {
    Map<String, Object> m = lineToMap(line);
    m.put("status", status);
    return m;
  }

  private Map<String, Object> toTeamMember(EmployeeEntity emp) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", emp.id.toString());
    m.put("userId", emp.userProfileId.toString());
    m.put("displayName", nz(emp.displayName));
    m.put("email", userEmail(emp.userProfileId));
    m.put("role", nz(emp.role));
    m.put("payMethod", nz(emp.payMethod));
    m.put("payRate", emp.payRate);
    m.put("bonusPercentage", emp.bonusPercentage);
    m.put("targetPeriod", nz(emp.targetPeriod));
    m.put("targetValue", emp.targetValue);
    m.put("active", emp.isActive);
    m.put("hiredAt", emp.hiredAt != null ? emp.hiredAt.toString() : null);
    m.put(
        "permissions",
        com.productstore.platform.constants.MerchantPermission.names(
            com.productstore.platform.constants.MerchantPermission.resolveStored(emp.merchantPermissions)));
    m.put("permissionsDefault", emp.merchantPermissions == null);
    return m;
  }

  private String userEmail(UUID userId) {
    return users.findById(userId).map(u -> u.email).orElse("");
  }

  private static boolean inPeriod(Instant when, LocalDate start, LocalDate end) {
    if (start == null && end == null) return true;
    if (when == null) return false;
    LocalDate d = when.atZone(ZoneOffset.UTC).toLocalDate();
    return !d.isBefore(start) && !d.isAfter(end);
  }

  private static void requireBothOrNeither(LocalDate start, LocalDate end) {
    if ((start == null) != (end == null)) {
      throw new IllegalArgumentException("provide_both_start_and_end_or_neither");
    }
  }

  private static String normalizePayMethod(String raw) {
    if (raw == null || raw.isBlank()) return EmployeePayMethod.PER_SERVICE.name();
    String u = raw.trim().toUpperCase();
    for (EmployeePayMethod m : EmployeePayMethod.values()) {
      if (m.name().equals(u)) return m.name();
    }
    throw new IllegalArgumentException("invalid_pay_method");
  }

  private static String normalizeJobType(String raw) {
    if (raw == null) return "";
    String u = raw.trim().toUpperCase().replace('-', '_');
    if ("ORDER".equals(u) || "ORDERS".equals(u)) return JOB_ORDER;
    if ("SALON_BOOKING".equals(u) || "BOOKING".equals(u) || "SALON".equals(u)) {
      return JOB_SALON_BOOKING;
    }
    return u;
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  private static final class JobLine {
    final UUID jobId;
    final String jobType;
    final Instant when;
    final double expectedAmount;
    final double units;
    boolean employerPaid;

    JobLine(
        UUID jobId,
        String jobType,
        Instant when,
        double expectedAmount,
        double units,
        boolean employerPaid) {
      this.jobId = jobId;
      this.jobType = jobType;
      this.when = when;
      this.expectedAmount = expectedAmount;
      this.units = units;
      this.employerPaid = employerPaid;
    }
  }
}
