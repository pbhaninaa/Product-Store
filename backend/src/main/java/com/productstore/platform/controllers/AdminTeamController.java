package com.productstore.platform.controllers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.services.EmployeeService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/m/{merchantSlug}/admin/team")
public class AdminTeamController {
  private final TenantAccessService tenantAccess;
  private final MembershipRepository memberships;
  private final EmployeeService employeeService;

  public AdminTeamController(
      TenantAccessService tenantAccess,
      MembershipRepository memberships,
      EmployeeService employeeService) {
    this.tenantAccess = tenantAccess;
    this.memberships = memberships;
    this.employeeService = employeeService;
  }

  @GetMapping
  public Map<String, Object> list(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    return Map.of("team", employeeService.listTeam(tenant.id()));
  }

  @PostMapping
  public Map<String, Object> create(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    Map<String, Object> member =
        employeeService.createEmployee(
            tenant.id(),
            principal.userId(),
            str(body, "email"),
            str(body, "password"),
            str(body, "displayName"),
            str(body, "role"),
            str(body, "payMethod"),
            dbl(body, "payRate"),
            dbl(body, "bonusPercentage"));
    return Map.of("ok", true, "member", member);
  }

  @PutMapping("/{employeeId}")
  public Map<String, Object> update(
      @PathVariable String merchantSlug,
      @PathVariable UUID employeeId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    Boolean active = body.containsKey("active") ? Boolean.valueOf(String.valueOf(body.get("active"))) : null;
    Map<String, Object> member =
        employeeService.updateEmployee(
            tenant.id(),
            employeeId,
            str(body, "displayName"),
            str(body, "role"),
            str(body, "payMethod"),
            dbl(body, "payRate"),
            dbl(body, "bonusPercentage"),
            active);
    return Map.of("ok", true, "member", member);
  }

  @DeleteMapping("/{employeeId}")
  public Map<String, Object> deactivate(
      @PathVariable String merchantSlug,
      @PathVariable UUID employeeId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    employeeService.deactivateEmployee(tenant.id(), employeeId);
    return Map.of("ok", true);
  }

  @GetMapping("/payment-calculations")
  public Map<String, Object> paymentCalculations(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    return employeeService.paymentCalculations(tenant.id(), startDate, endDate);
  }

  @GetMapping("/my-expected-income")
  public Map<String, Object> myIncome(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    return employeeService.myExpectedIncome(tenant.id(), principal.userId(), startDate, endDate);
  }

  @PostMapping("/payroll-marks")
  public Map<String, Object> markPaid(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    UUID employeeId = UUID.fromString(str(body, "employeeId"));
    UUID jobId = UUID.fromString(str(body, "jobId"));
    Boolean includeBonus =
        body.containsKey("includeBonus") ? Boolean.valueOf(String.valueOf(body.get("includeBonus"))) : null;
    employeeService.markJobPaid(
        tenant.id(), principal.userId(), employeeId, jobId, str(body, "jobType"), includeBonus);
    return Map.of("ok", true);
  }

  @DeleteMapping("/payroll-marks")
  public Map<String, Object> unmarkPaid(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    UUID employeeId = UUID.fromString(str(body, "employeeId"));
    UUID jobId = UUID.fromString(str(body, "jobId"));
    employeeService.unmarkJobPaid(tenant.id(), employeeId, jobId, str(body, "jobType"));
    return Map.of("ok", true);
  }

  @PostMapping("/payroll-marks/pay-all")
  public Map<String, Object> payAll(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireOwner(principal, tenant.id());
    UUID employeeId = UUID.fromString(str(body, "employeeId"));
    Boolean includeBonus =
        body.containsKey("includeBonus") ? Boolean.valueOf(String.valueOf(body.get("includeBonus"))) : null;
    int marked =
        employeeService.payAllPending(tenant.id(), principal.userId(), employeeId, includeBonus);
    return Map.of("ok", true, "marked", marked);
  }

  @PostMapping("/assign-job")
  public Map<String, Object> assignJob(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    requireMerchant(principal, tenant.id());
    UUID jobId = UUID.fromString(str(body, "jobId"));
    UUID employeeId =
        body.get("employeeId") == null || String.valueOf(body.get("employeeId")).isBlank()
            ? null
            : UUID.fromString(String.valueOf(body.get("employeeId")));
    employeeService.assignCompletedWork(tenant.id(), str(body, "jobType"), jobId, employeeId);
    return Map.of("ok", true);
  }

  private void requireOwner(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(principal.userId(), tenantId, List.of(Role.MERCHANT_OWNER))
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }

  private void requireMerchant(ApiUserPrincipal principal, UUID tenantId) {
    if (principal == null) throw new IllegalArgumentException("not_authenticated");
    memberships
        .findFirstByUserIdAndTenantIdAndRoleIn(
            principal.userId(), tenantId, List.of(Role.MERCHANT_OWNER, Role.MERCHANT_STAFF))
        .orElseThrow(() -> new IllegalArgumentException("forbidden"));
  }

  private static String str(Map<String, Object> body, String key) {
    Object v = body == null ? null : body.get(key);
    return v == null ? null : String.valueOf(v).trim();
  }

  private static Double dbl(Map<String, Object> body, String key) {
    Object v = body == null ? null : body.get(key);
    if (v == null || String.valueOf(v).isBlank()) return null;
    return Double.valueOf(String.valueOf(v));
  }
}
