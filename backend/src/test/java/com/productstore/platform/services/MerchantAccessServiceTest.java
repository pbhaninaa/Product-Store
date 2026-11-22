package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.constants.MerchantPermission;
import com.productstore.platform.entities.EmployeeEntity;
import com.productstore.platform.entities.MembershipEntity;
import com.productstore.platform.repositories.EmployeeRepository;
import com.productstore.platform.repositories.MembershipRepository;
import com.productstore.platform.services.auth.ApiUserPrincipal;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerchantAccessServiceTest {
  private final UUID tenantA = UUID.randomUUID();
  private final UUID tenantB = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  private MembershipRepository memberships;
  private EmployeeRepository employees;
  private MerchantAccessService service;
  private ApiUserPrincipal principal;

  @BeforeEach
  void setUp() {
    memberships = mock(MembershipRepository.class);
    employees = mock(EmployeeRepository.class);
    service = new MerchantAccessService(memberships, employees);
    principal = new ApiUserPrincipal(userId, "u@test.local", List.of(Role.MERCHANT_STAFF), "a");
  }

  private MembershipEntity membership(UUID tenantId, Role role) {
    MembershipEntity m = new MembershipEntity();
    m.id = UUID.randomUUID();
    m.userId = userId;
    m.tenantId = tenantId;
    m.role = role;
    m.createdAt = Instant.now();
    return m;
  }

  private void staffWithStored(String stored) {
    when(memberships.findAllByUserId(userId)).thenReturn(List.of(membership(tenantA, Role.MERCHANT_STAFF)));
    EmployeeEntity e = new EmployeeEntity();
    e.id = UUID.randomUUID();
    e.tenantId = tenantA;
    e.userProfileId = userId;
    e.merchantPermissions = stored;
    when(employees.findFirstByTenantIdAndUserProfileId(tenantA, userId)).thenReturn(Optional.of(e));
  }

  @Test
  void nullStoredPermissionsResolveToTodaysStaffAccess() {
    staffWithStored(null);
    var access = service.requireMember(principal, tenantA);
    assertThat(access.owner()).isFalse();
    assertThat(access.permissions()).isEqualTo(MerchantPermission.LEGACY_STAFF_DEFAULT);
  }

  @Test
  void legacyDefaultMatchesWhatStaffCouldDoBefore() {
    assertThat(MerchantPermission.LEGACY_STAFF_DEFAULT)
        .containsExactlyInAnyOrder(
            MerchantPermission.ORDERS,
            MerchantPermission.BOOKINGS,
            MerchantPermission.PAYMENTS,
            MerchantPermission.PROMOTIONS,
            MerchantPermission.INSIGHTS,
            MerchantPermission.CONFIRM_PAYMENTS,
            MerchantPermission.DELETE_RECORDS,
            MerchantPermission.CANCEL_BOOKINGS);
    assertThat(MerchantPermission.LEGACY_STAFF_DEFAULT)
        .doesNotContain(
            MerchantPermission.PRODUCTS,
            MerchantPermission.SERVICES,
            MerchantPermission.TEAM_PAYROLL,
            MerchantPermission.STORE_SETTINGS);
  }

  @Test
  void emptyStoredPermissionsMeanNoOptionalAccess() {
    staffWithStored("");
    var access = service.requireMember(principal, tenantA);
    assertThat(access.permissions()).isEmpty();
    assertThatThrownBy(() -> service.requireAll(principal, tenantA, MerchantPermission.ORDERS))
        .hasMessage("permission_denied");
  }

  @Test
  void storedPermissionsAreUsedAsIs() {
    staffWithStored("ORDERS,PRODUCTS");
    assertThat(service.requireAll(principal, tenantA, MerchantPermission.PRODUCTS).permissions())
        .containsExactlyInAnyOrder(MerchantPermission.ORDERS, MerchantPermission.PRODUCTS);
    assertThatThrownBy(
            () -> service.requireAll(principal, tenantA, MerchantPermission.ORDERS, MerchantPermission.DELETE_RECORDS))
        .hasMessage("permission_denied");
    assertThat(service.requireAny(principal, tenantA, MerchantPermission.INSIGHTS, MerchantPermission.ORDERS))
        .isNotNull();
  }

  @Test
  void ownerAlwaysHasEverythingAndOwnerOnlyActions() {
    when(memberships.findAllByUserId(userId))
        .thenReturn(List.of(membership(tenantA, Role.MERCHANT_STAFF), membership(tenantA, Role.MERCHANT_OWNER)));
    var access = service.requireOwner(principal, tenantA);
    assertThat(access.owner()).isTrue();
    assertThat(access.permissions()).isEqualTo(EnumSet.allOf(MerchantPermission.class));
  }

  @Test
  void staffCannotTakeOwnerOnlyActions() {
    staffWithStored("ORDERS,BOOKINGS,PAYMENTS,PRODUCTS,SERVICES,PROMOTIONS,TEAM_PAYROLL,STORE_SETTINGS,INSIGHTS,"
        + "CONFIRM_PAYMENTS,DELETE_RECORDS,CANCEL_BOOKINGS");
    assertThatThrownBy(() -> service.requireOwner(principal, tenantA)).hasMessage("permission_denied");
  }

  @Test
  void membershipOfAnotherStoreGivesNoAccess() {
    staffWithStored(null);
    assertThatThrownBy(() -> service.requireMember(principal, tenantB)).hasMessage("forbidden");
    assertThatThrownBy(() -> service.requireMember(null, tenantA)).hasMessage("not_authenticated");
  }

  @Test
  void staffMembershipWithoutEmployeeRowKeepsTodaysAccess() {
    when(memberships.findAllByUserId(userId)).thenReturn(List.of(membership(tenantA, Role.MERCHANT_STAFF)));
    when(employees.findFirstByTenantIdAndUserProfileId(tenantA, userId)).thenReturn(Optional.empty());
    assertThat(service.requireMember(principal, tenantA).permissions())
        .isEqualTo(MerchantPermission.LEGACY_STAFF_DEFAULT);
  }

  @Test
  void parsingIgnoresUnknownStoredNamesButRejectsUnknownOwnerInput() {
    assertThat(MerchantPermission.parseCsv(" orders , NOPE,,BOOKINGS"))
        .containsExactlyInAnyOrder(MerchantPermission.ORDERS, MerchantPermission.BOOKINGS);
    assertThatThrownBy(() -> MerchantPermission.parseStrict(List.of("ORDERS", "NOPE")))
        .hasMessage("invalid_permission");
    assertThat(MerchantPermission.toCsv(EnumSet.noneOf(MerchantPermission.class))).isEmpty();
    assertThat(MerchantPermission.toCsv(EnumSet.of(MerchantPermission.INSIGHTS, MerchantPermission.ORDERS)))
        .isEqualTo("ORDERS,INSIGHTS");
  }
}
