package com.productstore.platform.services;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.EmployeeEntity;
import com.productstore.platform.entities.SalonStaffEntity;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.SalonStaffRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps salon booking staff in sync with Team &amp; Payroll so merchants only set bookable windows.
 */
@Service
public class SalonStaffSyncService {
  private final EmployeeRepository employees;
  private final SalonStaffRepository staff;

  public SalonStaffSyncService(EmployeeRepository employees, SalonStaffRepository staff) {
    this.employees = employees;
    this.staff = staff;
  }

  @Transactional
  public void syncTenantFromPayroll(UUID tenantId) {
    if (tenantId == null) return;
    for (EmployeeEntity emp : employees.findByTenantId(tenantId)) {
      upsertFromEmployee(emp);
    }
  }

  @Transactional
  public void upsertFromEmployee(EmployeeEntity emp) {
    if (emp == null || emp.id == null || emp.tenantId == null) return;

    Optional<SalonStaffEntity> linked = staff.findByTenantIdAndEmployeeId(emp.tenantId, emp.id);
    if (linked.isPresent()) {
      applyEmployee(linked.get(), emp);
      staff.save(linked.get());
      return;
    }

    if (!emp.isActive) {
      return;
    }

    SalonStaffEntity unmatched = findUnlinkedByDisplayName(emp.tenantId, emp.displayName);
    if (unmatched != null) {
      applyEmployee(unmatched, emp);
      staff.save(unmatched);
      return;
    }

    SalonStaffEntity created = new SalonStaffEntity();
    created.id = UUID.randomUUID();
    created.tenantId = emp.tenantId;
    created.createdAt = Instant.now();
    applyEmployee(created, emp);
    staff.save(created);
  }

  private SalonStaffEntity findUnlinkedByDisplayName(UUID tenantId, String displayName) {
    String want = normalizeName(displayName);
    if (want.isEmpty()) return null;
    List<SalonStaffEntity> rows = staff.findByTenantIdOrderByCreatedAtDesc(tenantId);
    for (SalonStaffEntity row : rows) {
      if (row.employeeId != null) continue;
      if (want.equals(normalizeName(row.displayName))) {
        return row;
      }
    }
    return null;
  }

  private static void applyEmployee(SalonStaffEntity row, EmployeeEntity emp) {
    row.employeeId = emp.id;
    String name = emp.displayName == null ? "" : emp.displayName.trim();
    row.displayName = name.isEmpty() ? "Team member" : name;
    row.active = emp.isActive;
  }

  private static String normalizeName(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }
}
