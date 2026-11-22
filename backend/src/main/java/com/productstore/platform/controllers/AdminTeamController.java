package com.productstore.platform.controllers;

import static com.productstore.platform.constants.MerchantPermission.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.productstore.platform.services.EmployeeService;
import com.productstore.platform.services.MerchantAccessService;
import com.productstore.platform.services.TenantAccessService;
import com.productstore.platform.services.auth.ApiUserPrincipal;

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
  private final MerchantAccessService access;
  private final EmployeeService employeeService;

  public AdminTeamController(
      TenantAccessService tenantAccess,
      MerchantAccessService access,
      EmployeeService employeeService) {
    this.tenantAccess = tenantAccess;
    this.access = access;
    this.employeeService = employeeService;
  }

  @GetMapping
  public Map<String, Object> list(
      @PathVariable String merchantSlug, @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireAll(principal, tenant.id(), TEAM_PAYROLL);
    return Map.of("team", employeeService.listTeam(tenant.id()));
  }

  @PostMapping
  public Map<String, Object> create(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireOwner(principal, tenant.id());
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
    access.requireOwner(principal, tenant.id());
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
    access.requireOwner(principal, tenant.id());
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
    access.requireAll(principal, tenant.id(), TEAM_PAYROLL);
    return employeeService.paymentCalculations(tenant.id(), startDate, endDate);
  }

  @GetMapping("/my-expected-income")
  public Map<String, Object> myIncome(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireMember(principal, tenant.id());
    return employeeService.myExpectedIncome(tenant.id(), principal.userId(), startDate, endDate);
  }

  @PostMapping("/payroll-marks")
  public Map<String, Object> markPaid(
      @PathVariable String merchantSlug,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireAll(principal, tenant.id(), TEAM_PAYROLL);
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
    access.requireAll(principal, tenant.id(), TEAM_PAYROLL);
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
    access.requireAll(principal, tenant.id(), TEAM_PAYROLL);
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
    access.requireAll(principal, tenant.id(), TEAM_PAYROLL);
    UUID jobId = UUID.fromString(str(body, "jobId"));
    UUID employeeId =
        body.get("employeeId") == null || String.valueOf(body.get("employeeId")).isBlank()
            ? null
            : UUID.fromString(String.valueOf(body.get("employeeId")));
    employeeService.assignCompletedWork(tenant.id(), str(body, "jobType"), jobId, employeeId);
    return Map.of("ok", true);
  }

  /** Owner reads what one staff member may do. */
  @GetMapping("/{employeeId}/permissions")
  public Map<String, Object> permissions(
      @PathVariable String merchantSlug,
      @PathVariable UUID employeeId,
      @AuthenticationPrincipal ApiUserPrincipal principal) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireOwner(principal, tenant.id());
    return access.staffPermissions(tenant.id(), employeeId);
  }

  /**
   * Owner sets what one staff member may do: {@code {"permissions": ["ORDERS", ...]}} stores exactly
   * that list; {@code {"resetToDefault": true}} goes back to the default staff access.
   */
  @PutMapping("/{employeeId}/permissions")
  public Map<String, Object> updatePermissions(
      @PathVariable String merchantSlug,
      @PathVariable UUID employeeId,
      @AuthenticationPrincipal ApiUserPrincipal principal,
      @RequestBody Map<String, Object> body) {
    var tenant = tenantAccess.requireTenantBySlug(merchantSlug);
    access.requireOwner(principal, tenant.id());
    boolean reset = body != null && Boolean.parseBoolean(String.valueOf(body.get("resetToDefault")));
    Object raw = body == null ? null : body.get("permissions");
    if (!reset && !(raw instanceof List<?>)) throw new IllegalArgumentException("invalid_permission");
    List<String> names =
        reset ? List.of() : ((List<?>) raw).stream().map(v -> v == null ? "" : String.valueOf(v)).toList();
    Map<String, Object> out = new java.util.LinkedHashMap<>(access.updateStaffPermissions(tenant.id(), employeeId, names, reset));
    out.put("ok", true);
    return out;
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
