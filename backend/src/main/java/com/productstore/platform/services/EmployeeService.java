package com.productstore.platform.services;

import java.math.BigDecimal;
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
      PayInput pay) {
    subscriptions.assertCanAddEmployee(tenantId);
    if (email == null || email.isBlank() || password == null || password.isBlank()) {
      throw new IllegalArgumentException("email_and_password_required");
    }
    String normalizedEmail = email.trim().toLowerCase();
    if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
      throw new IllegalStateException("email_already_exists");
    }
    String method = EmployeePayRules.normalizeServicePayMethod(pay.payMethod());
    EmployeePayRules.validateServiceRate(method, pay.payRate());
    String orderMethod = EmployeePayRules.normalizeOrderPayMethod(pay.orderPayMethod());
    EmployeePayRules.validateOrderRate(orderMethod, pay.orderPayRate());

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
    emp.payRate = pay.payRate();
    emp.bonusPercentage = pay.bonusPercentage();
    emp.orderPayMethod = orderMethod;
    emp.orderPayRate = pay.orderPayRate();
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
      PayInput pay,
      Boolean active) {
    EmployeeEntity emp =
        employees
            .findById(employeeId)
            .filter(e -> e.tenantId.equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("employee_not_found"));
    // Validate the resulting method + rate pair before changing anything. Past payouts are unaffected:
    // marked jobs keep the amount snapshotted when they were marked.
    boolean serviceChange = notBlank(pay.payMethod()) || pay.payRate() != null;
    String method =
        notBlank(pay.payMethod())
            ? EmployeePayRules.normalizeServicePayMethod(pay.payMethod())
            : emp.payMethod;
    Double rate = pay.payRate() != null ? pay.payRate() : emp.payRate;
    if (serviceChange) EmployeePayRules.validateServiceRate(method, rate);
    boolean orderChange = notBlank(pay.orderPayMethod()) || pay.orderPayRate() != null;
    String orderMethod =
        notBlank(pay.orderPayMethod())
            ? EmployeePayRules.normalizeOrderPayMethod(pay.orderPayMethod())
            : EmployeePayRules.orderPayMethod(emp).name();
    Double orderRate = pay.orderPayRate() != null ? pay.orderPayRate() : emp.orderPayRate;
    if (orderChange) EmployeePayRules.validateOrderRate(orderMethod, orderRate);

    if (displayName != null && !displayName.isBlank()) emp.displayName = displayName.trim();
    if (roleLabel != null && !roleLabel.isBlank()) emp.role = roleLabel.trim();
    if (serviceChange) {
      emp.payMethod = method;
      emp.payRate = rate;
    }
    if (pay.bonusPercentage() != null) emp.bonusPercentage = pay.bonusPercentage();
    if (orderChange) {
      emp.orderPayMethod = orderMethod;
      emp.orderPayRate = orderRate;
    }
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
      List<JobLine> lines = payrollLines(tenantId, emp, startDate, endDate);
      double servicePending = sumPending(emp, serviceLines(lines));
      double orderPending = sumUnpaid(orderLines(lines));
      double units = unitsForUnpaid(emp, serviceLines(lines)) + unpaidCount(orderLines(lines));
      int jobCount = unpaidCount(lines);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("employeeId", emp.id.toString());
      row.put("displayName", nz(emp.displayName));
      row.put("email", userEmail(emp.userProfileId));
      row.put("role", nz(emp.role));
      putPaySettings(row, emp);
      row.put("servicePending", money(servicePending));
      row.put("orderPending", money(orderPending));
      row.put("pendingExpected", money(servicePending + orderPending));
      row.put("units", money(units));
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

  /**
   * Staff "My income". Totals are split into service pay (bookings) and order pay (orders); {@code owedTotal},
   * {@code upcomingTotal} and {@code paidOutTotal} are the sum of both.
   */
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
      out.put("orderPayMethod", "");
      out.put("orderPayRate", null);
      out.put("payPeriodStart", startDate != null ? startDate.toString() : null);
      out.put("payPeriodEnd", endDate != null ? endDate.toString() : null);
      out.put("pendingExpected", 0.0);
      putSplit(out, "owed", 0.0, 0.0);
      out.put("owedCount", 0);
      putSplit(out, "upcoming", 0.0, 0.0);
      out.put("upcomingCount", 0);
      putSplit(out, "paidOut", 0.0, 0.0);
      out.put("lines", List.of());
      return out;
    }
    EmployeeEntity emp = empOpt.get();
    // Owed + Paid out: exactly the lines owner payroll sees (same builder, same marks).
    List<JobLine> payrollLines = payrollLines(tenantId, emp, startDate, endDate);
    List<JobLine> owed = payrollLines.stream().filter(l -> !l.employerPaid).toList();
    List<JobLine> paidOut = payrollLines.stream().filter(l -> l.employerPaid).toList();
    // Upcoming: assigned to this employee but not payable yet. Display-only; never fed to payroll.
    Set<String> payrollJobs = payrollLines.stream().map(JobLine::key).collect(Collectors.toSet());
    List<JobLine> upcoming =
        buildUpcomingLines(tenantId, emp, startDate, endDate, payrollJobs, Instant.now());

    double owedService = sumPending(emp, serviceLines(payrollLines));
    double owedOrder = sumUnpaid(orderLines(payrollLines));
    List<JobLine> owedPlusUpcoming = new ArrayList<>(serviceLines(owed));
    owedPlusUpcoming.addAll(serviceLines(upcoming));
    // What the upcoming work adds on top of what is already owed (a salary or a day already counted is not
    // counted twice).
    double upcomingService =
        Math.max(0.0, payFor(emp, owedPlusUpcoming) - payFor(emp, serviceLines(owed)));
    double upcomingOrder = sumAll(orderLines(upcoming));
    // Paid out: what each mark paid (snapshot), so later setting changes never move it.
    double paidService = sumAll(serviceLines(paidOut));
    double paidOrder = sumAll(orderLines(paidOut));

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
    putPaySettings(out, emp);
    out.put("payPeriodStart", startDate != null ? startDate.toString() : null);
    out.put("payPeriodEnd", endDate != null ? endDate.toString() : null);
    out.put("pendingExpected", money(owedService + owedOrder)); // kept for older clients; = owedTotal
    putSplit(out, "owed", owedService, owedOrder);
    out.put("owedCount", owed.size());
    putSplit(out, "upcoming", upcomingService, upcomingOrder);
    out.put("upcomingCount", upcoming.size());
    putSplit(out, "paidOut", paidService, paidOrder);
    out.put("lines", outLines);
    return out;
  }

  /** {@code <prefix>Total}, {@code <prefix>ServiceTotal} and {@code <prefix>OrderTotal}. */
  private static void putSplit(Map<String, Object> out, String prefix, double service, double order) {
    out.put(prefix + "Total", money(service + order));
    out.put(prefix + "ServiceTotal", money(service));
    out.put(prefix + "OrderTotal", money(order));
  }

  /** Service pay and order pay settings, as shown on team, payroll and My income. */
  private static void putPaySettings(Map<String, Object> out, EmployeeEntity emp) {
    out.put("payMethod", nz(emp.payMethod));
    out.put("payRate", emp.payRate);
    out.put("bonusPercentage", emp.bonusPercentage);
    out.put("orderPayMethod", EmployeePayRules.orderPayMethod(emp).name());
    out.put("orderPayRate", emp.orderPayRate);
  }

  /**
   * Work assigned to {@code emp} that owner payroll does not (yet) treat as payable — shown to the employee
   * as "Upcoming" on My income only. Payroll ({@link #buildJobLines}) pays a booking once it is credited to the
   * employee ({@code completedByEmployeeId}) and confirmed / in progress / completed; those are "Owed" (or
   * "Paid out" once marked), never Upcoming. A booking is Upcoming when:
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
   * credited to another employee is theirs. The date filter uses the appointment start; no range means all
   * such bookings.
   *
   * <p>An order is Upcoming when it is paid, credited to {@code emp}, not completed yet (processing / ready),
   * and {@code emp} has order pay. It becomes Owed once completed. Orders have no assignment before payment.
   * The date filter uses the payment date, like payroll.
   */
  private List<JobLine> buildUpcomingLines(
      UUID tenantId,
      EmployeeEntity emp,
      LocalDate start,
      LocalDate end,
      Set<String> alreadyOnPayroll,
      Instant now) {
    List<JobLine> lines = new ArrayList<>();
    if (EmployeePayRules.paysForOrders(emp)) {
      for (OrderEntity o : orders.findPaidByTenantAndEmployee(tenantId, emp.id)) {
        if (orderCompleted(o) || alreadyOnPayroll.contains(JOB_ORDER + ":" + o.id)) continue;
        Instant when = orderWhen(o);
        if (!inPeriod(when, start, end)) continue;
        lines.add(orderLine(emp, o, when));
      }
    }
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
    for (SalonBookingEntity b : candidates.values()) {
      if (alreadyOnPayroll.contains(JOB_SALON_BOOKING + ":" + b.id)) continue;
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
    // Only a job on this employee's payroll can be marked (credited to them and payable).
    List<JobLine> lines = buildJobLines(tenantId, emp, null, null);
    String key = jobType + ":" + jobId;
    JobLine target =
        lines.stream()
            .filter(l -> l.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("job_not_attributed"));
    applyMarks(tenantId, emp.id, lines);
    if (target.employerPaid) return;
    recordMark(tenantId, actorUserId, emp, target, paidContribution(emp, lines, target), includeBonus);
  }

  /**
   * What marking {@code target} paid: how much the amount owed for its kind of pay goes down. Order pay and
   * per-job service pay: the line amount. A weekly / monthly salary is owed once while any job is unpaid, and
   * a per-day rate once per day, so only the mark that clears the last unpaid job (of the period / day) carries
   * it; the others paid 0. {@code lines}' marks must be applied.
   */
  private double paidContribution(EmployeeEntity emp, List<JobLine> lines, JobLine target) {
    if (target.isOrder()) return target.expectedAmount;
    List<JobLine> unpaid = serviceLines(lines).stream().filter(l -> !l.employerPaid).toList();
    List<JobLine> unpaidAfter = unpaid.stream().filter(l -> l != target).toList();
    return money(sumPending(emp, unpaid) - sumPending(emp, unpaidAfter));
  }

  /** Saves the mark with its paid amount snapshot and tells the employee. */
  private void recordMark(
      UUID tenantId,
      UUID actorUserId,
      EmployeeEntity emp,
      JobLine line,
      double paidAmount,
      Boolean includeBonus) {
    EmployeePayrollJobMarkEntity mark = new EmployeePayrollJobMarkEntity();
    mark.tenantId = tenantId;
    mark.employeeRecordId = emp.id;
    mark.jobId = line.jobId;
    mark.jobType = line.jobType;
    mark.markedByUsername = actorUserId != null ? actorUserId.toString() : null;
    mark.includeBonus = includeBonus;
    mark.paidAmount = paidAmount;
    mark.jobAt = line.when;
    payrollMarks.save(mark);
    line.employerPaid = true;
    inAppNotifications.notifyUser(
        emp.userProfileId,
        tenantId,
        "Payroll paid",
        "A job was marked paid by your employer.",
        "PAYROLL_PAID",
        line.jobType,
        line.jobId.toString());
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
    subscriptions.assertCanUsePayroll(tenantId);
    EmployeeEntity emp = requireActiveEmployee(tenantId, employeeId);
    List<JobLine> lines = buildJobLines(tenantId, emp, null, null);
    applyMarks(tenantId, emp.id, lines);
    List<JobLine> unpaid =
        lines.stream()
            .filter(l -> !l.employerPaid)
            .sorted(Comparator.comparing((JobLine l) -> l.when, Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList();
    for (JobLine line : unpaid) {
      // Contribution is worked out before recordMark flips the line to paid.
      recordMark(tenantId, actorUserId, emp, line, paidContribution(emp, lines, line), includeBonus);
    }
    return unpaid.size();
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
   * having to assign it. Runs after a booking is confirmed (cash code, EFT approval, online payment) or moved to
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

  /**
   * Payable work credited to {@code emp}, priced with the current settings (marks not applied):
   *
   * <ul>
   *   <li>orders: paid (never cancelled), credited to {@code emp} ({@code completedByEmployeeId}) and completed
   *       (fulfilment {@code completed}); only when {@code emp} has order pay (NONE lists no orders);
   *   <li>bookings: credited to {@code emp} and confirmed / in progress / completed (service pay).
   * </ul>
   */
  private List<JobLine> buildJobLines(
      UUID tenantId, EmployeeEntity emp, LocalDate start, LocalDate end) {
    List<JobLine> lines = new ArrayList<>();
    if (EmployeePayRules.paysForOrders(emp)) {
      for (OrderEntity o : orders.findPaidByTenantAndEmployee(tenantId, emp.id)) {
        if (!orderCompleted(o)) continue;
        Instant when = orderWhen(o);
        if (!inPeriod(when, start, end)) continue;
        lines.add(orderLine(emp, o, when));
      }
    }
    for (SalonBookingEntity b : bookings.findConfirmedByTenantAndEmployee(tenantId, emp.id)) {
      Instant when = b.completedAt != null ? b.completedAt : b.startAt;
      if (!inPeriod(when, start, end)) continue;
      lines.add(bookingLine(emp, b, when));
    }
    return lines;
  }

  private static boolean orderCompleted(OrderEntity o) {
    return OrderEntity.effectiveFulfillment(o) == OrderEntity.FulfillmentStatus.completed;
  }

  /** Payroll date of an order: when it was paid (credited). */
  private static Instant orderWhen(OrderEntity o) {
    return o.completedAt != null ? o.completedAt : o.paymentConfirmedAt;
  }

  /** Order pay for one order (fixed per order, or a percentage of the order total less delivery). */
  private static JobLine orderLine(EmployeeEntity emp, OrderEntity o, Instant when) {
    return new JobLine(o.id, JOB_ORDER, when, EmployeePayRules.orderPay(emp, o), 1.0, false);
  }

  /**
   * Service pay for one booking (pay method / rate / bonus). Only PERCENT_OF_SERVICE uses the price: the
   * booking's {@code amountZar}, fixed when booked, so later service price edits never change it.
   */
  private JobLine bookingLine(EmployeeEntity emp, SalonBookingEntity b, Instant when) {
    Duration dur =
        (b.startAt != null && b.endAt != null && b.endAt.isAfter(b.startAt))
            ? Duration.between(b.startAt, b.endAt)
            : Duration.ofHours(1);
    double hours = Math.max(1.0, dur.toMinutes() / 60.0);
    return new JobLine(
        b.id,
        JOB_SALON_BOOKING,
        when,
        EmployeePayRules.servicePay(emp, hours, b.amountZar),
        unitsForJob(emp, hours),
        false);
  }

  private double unitsForJob(EmployeeEntity emp, double hours) {
    String method = emp.payMethod == null ? "" : emp.payMethod.trim().toUpperCase();
    if (EmployeePayMethod.PER_HOUR.name().equals(method)) return hours;
    if (EmployeePayMethod.PER_DAY.name().equals(method)) return 1.0;
    if (EmployeePayMethod.WEEKLY.name().equals(method)
        || EmployeePayMethod.MONTHLY.name().equals(method)) return 0.0;
    return 1.0;
  }

  /**
   * Owner payroll / My income lines for the period: {@link #buildJobLines} with marks applied, plus paid
   * jobs that are no longer payable under the current rules (e.g. order pay since switched to NONE, or the
   * booking later cancelled) so a payout already made never disappears. Paid lines carry their snapshot.
   */
  private List<JobLine> payrollLines(
      UUID tenantId, EmployeeEntity emp, LocalDate start, LocalDate end) {
    List<JobLine> lines = buildJobLines(tenantId, emp, start, end);
    Map<String, EmployeePayrollJobMarkEntity> marks = applyMarks(tenantId, emp.id, lines);
    Set<String> listed = lines.stream().map(JobLine::key).collect(Collectors.toSet());
    for (EmployeePayrollJobMarkEntity m : marks.values()) {
      if (m.paidAmount == null || listed.contains(m.jobType + ":" + m.jobId)) continue;
      if (!inPeriod(m.jobAt, start, end)) continue;
      lines.add(new JobLine(m.jobId, m.jobType, m.jobAt, m.paidAmount, 0.0, true));
    }
    return lines;
  }

  /**
   * Flags lines that have a payroll mark as paid and gives them the amount snapshotted when they were marked
   * (a mark without a snapshot keeps the live amount). Returns the employee's marks by job key.
   */
  private Map<String, EmployeePayrollJobMarkEntity> applyMarks(
      UUID tenantId, UUID employeeId, List<JobLine> lines) {
    Map<String, EmployeePayrollJobMarkEntity> marks = new LinkedHashMap<>();
    for (EmployeePayrollJobMarkEntity m :
        payrollMarks.findByTenantIdAndEmployeeRecordId(tenantId, employeeId)) {
      marks.putIfAbsent(m.jobType + ":" + m.jobId, m);
    }
    for (JobLine line : lines) {
      EmployeePayrollJobMarkEntity m = marks.get(line.key());
      line.employerPaid = m != null;
      if (m != null && m.paidAmount != null) line.expectedAmount = m.paidAmount;
    }
    return marks;
  }

  /**
   * Service pay still owed for {@code lines} (unpaid ones only): a weekly / monthly salary once while any job
   * is unpaid, a per-day rate once per distinct unpaid day, otherwise the sum of the line amounts.
   */
  private double sumPending(EmployeeEntity emp, List<JobLine> lines) {
    String method = emp.payMethod == null ? "" : emp.payMethod.trim().toUpperCase();
    boolean fixed =
        EmployeePayMethod.WEEKLY.name().equals(method)
            || EmployeePayMethod.MONTHLY.name().equals(method);
    BigDecimal rate = BigDecimal.valueOf(emp.payRate == null ? 0.0 : emp.payRate);
    if (fixed) {
      boolean anyPending = lines.stream().anyMatch(l -> !l.employerPaid);
      if (!anyPending) return 0.0;
      return EmployeePayRules.withBonus(rate, emp.bonusPercentage);
    }
    if (EmployeePayMethod.PER_DAY.name().equals(method)) {
      Set<LocalDate> unpaidDays = new HashSet<>();
      for (JobLine l : lines) {
        if (l.employerPaid || l.when == null) continue;
        unpaidDays.add(l.when.atZone(ZoneOffset.UTC).toLocalDate());
      }
      return EmployeePayRules.withBonus(
          rate.multiply(BigDecimal.valueOf(unpaidDays.size())), emp.bonusPercentage);
    }
    return sumUnpaid(lines);
  }

  /** Sum of the unpaid line amounts, rounded to the cent. */
  private static double sumUnpaid(List<JobLine> lines) {
    return sumAll(lines.stream().filter(l -> !l.employerPaid).toList());
  }

  /** Sum of the line amounts (each already in cents), rounded to the cent. */
  private static double sumAll(List<JobLine> lines) {
    BigDecimal sum = BigDecimal.ZERO;
    for (JobLine l : lines) sum = sum.add(BigDecimal.valueOf(l.expectedAmount));
    return money(sum.doubleValue());
  }

  private static int unpaidCount(List<JobLine> lines) {
    return (int) lines.stream().filter(l -> !l.employerPaid).count();
  }

  private static List<JobLine> serviceLines(List<JobLine> lines) {
    return lines.stream().filter(l -> !l.isOrder()).toList();
  }

  private static List<JobLine> orderLines(List<JobLine> lines) {
    return lines.stream().filter(JobLine::isOrder).toList();
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
    putPaySettings(m, emp);
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

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  /** Rand amount rounded half up to the cent. */
  private static double money(double v) {
    return EmployeePayRules.money(v);
  }

  /**
   * Service pay and order pay settings from a create / update request. On update a null field means "leave
   * unchanged".
   */
  public record PayInput(
      String payMethod,
      Double payRate,
      Double bonusPercentage,
      String orderPayMethod,
      Double orderPayRate) {}

  /**
   * One-off snapshot of marks made before paid amounts were stored (paid_amount NULL). Each gets what the
   * pay rules at the time showed as paid, worked out with the employee's settings as they are now (unchanged
   * since: this runs at startup, before anyone can edit them under the new rules):
   *
   * <ul>
   *   <li>the old rules priced every credited paid order and confirmed booking with the service pay setting
   *       (an order counted as a one-hour job);
   *   <li>per-job methods: the line amount; per day: the rate on the first paid job of each day; weekly /
   *       monthly: the salary once, on the newest paid job.
   * </ul>
   *
   * Marks whose job is no longer credited to the employee were not shown before and stay NULL (still not
   * shown). Idempotent; returns how many marks were filled.
   */
  @Transactional
  public int snapshotLegacyMarks() {
    Map<UUID, List<EmployeePayrollJobMarkEntity>> byEmployee =
        payrollMarks.findByPaidAmountIsNull().stream()
            .collect(Collectors.groupingBy(m -> m.employeeRecordId, LinkedHashMap::new, Collectors.toList()));
    int filled = 0;
    for (var entry : byEmployee.entrySet()) {
      EmployeeEntity emp = employees.findById(entry.getKey()).orElse(null);
      if (emp == null) continue;
      Map<String, JobLine> legacy = new LinkedHashMap<>();
      for (OrderEntity o : orders.findPaidByTenantAndEmployee(emp.tenantId, emp.id)) {
        double pay = EmployeePayRules.servicePay(emp, 1.0, EmployeePayRules.orderPayBase(o));
        legacy.put(JOB_ORDER + ":" + o.id, new JobLine(o.id, JOB_ORDER, orderWhen(o), pay, 1.0, true));
      }
      for (SalonBookingEntity b : bookings.findConfirmedByTenantAndEmployee(emp.tenantId, emp.id)) {
        JobLine l = bookingLine(emp, b, b.completedAt != null ? b.completedAt : b.startAt);
        l.employerPaid = true;
        legacy.put(l.key(), l);
      }
      List<EmployeePayrollJobMarkEntity> marks =
          entry.getValue().stream()
              .filter(m -> emp.tenantId.equals(m.tenantId) && legacy.containsKey(m.jobType + ":" + m.jobId))
              .sorted(
                  Comparator.comparing(
                      (EmployeePayrollJobMarkEntity m) -> legacy.get(m.jobType + ":" + m.jobId).when,
                      Comparator.nullsFirst(Comparator.naturalOrder())))
              .toList();
      String method = emp.payMethod == null ? "" : emp.payMethod.trim().toUpperCase();
      boolean salary =
          EmployeePayMethod.WEEKLY.name().equals(method) || EmployeePayMethod.MONTHLY.name().equals(method);
      boolean perDay = EmployeePayMethod.PER_DAY.name().equals(method);
      double dayOrSalary =
          EmployeePayRules.withBonus(
              BigDecimal.valueOf(emp.payRate == null ? 0.0 : emp.payRate), emp.bonusPercentage);
      Set<LocalDate> paidDays = new HashSet<>();
      for (int i = 0; i < marks.size(); i++) {
        EmployeePayrollJobMarkEntity m = marks.get(i);
        JobLine line = legacy.get(m.jobType + ":" + m.jobId);
        double amount;
        if (salary) {
          amount = i == marks.size() - 1 ? dayOrSalary : 0.0;
        } else if (perDay) {
          LocalDate day = line.when == null ? null : line.when.atZone(ZoneOffset.UTC).toLocalDate();
          amount = day != null && paidDays.add(day) ? dayOrSalary : 0.0;
        } else {
          amount = line.expectedAmount;
        }
        m.paidAmount = amount;
        m.jobAt = line.when;
        payrollMarks.save(m);
        filled++;
      }
    }
    return filled;
  }

  private static final class JobLine {
    final UUID jobId;
    final String jobType;
    final Instant when;
    /** Live pay for unpaid lines; the paid amount snapshot once marked. */
    double expectedAmount;
    final double units;
    boolean employerPaid;

    String key() {
      return jobType + ":" + jobId;
    }

    boolean isOrder() {
      return JOB_ORDER.equals(jobType);
    }

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
