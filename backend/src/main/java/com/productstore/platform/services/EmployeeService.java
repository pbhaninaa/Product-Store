package com.productstore.platform.services;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import com.productstore.platform.repositories.UserRepository;
import com.productstore.platform.services.auth.PasswordHasher;
import com.productstore.platform.services.auth.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {
  public static final String JOB_ORDER = "ORDER";
  public static final String JOB_SALON_BOOKING = "SALON_BOOKING";

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
      SalonStaffSyncService salonStaffSync) {
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
    salonStaffSync.upsertFromEmployee(emp);
    return toTeamMember(emp);
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
      out.put("lines", List.of());
      return out;
    }
    EmployeeEntity emp = empOpt.get();
    List<JobLine> lines = buildJobLines(tenantId, emp, startDate, endDate);
    applyMarks(tenantId, emp.id, lines);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("employeeId", emp.id.toString());
    out.put("displayName", nz(emp.displayName));
    out.put("payMethod", nz(emp.payMethod));
    out.put("payRate", emp.payRate);
    out.put("bonusPercentage", emp.bonusPercentage);
    out.put("payPeriodStart", startDate != null ? startDate.toString() : null);
    out.put("payPeriodEnd", endDate != null ? endDate.toString() : null);
    out.put("pendingExpected", round2(sumPending(emp, lines)));
    out.put("lines", lines.stream().map(this::lineToMap).toList());
    return out;
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

  private EmployeeEntity requireActiveEmployee(UUID tenantId, UUID employeeId) {
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
      Duration dur =
          (b.startAt != null && b.endAt != null && b.endAt.isAfter(b.startAt))
              ? Duration.between(b.startAt, b.endAt)
              : Duration.ofHours(1);
      double hours = Math.max(1.0, dur.toMinutes() / 60.0);
      lines.add(
          new JobLine(
              b.id,
              JOB_SALON_BOOKING,
              when,
              expectedAmount(emp, hours, dur),
              unitsForJob(emp, hours),
              false));
    }
    return lines;
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
