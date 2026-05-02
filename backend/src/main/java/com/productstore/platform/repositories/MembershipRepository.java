package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.services.auth.Role;
import com.productstore.platform.entities.MembershipEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<MembershipEntity, UUID> {
  List<MembershipEntity> findAllByUserId(UUID userId);

  Optional<MembershipEntity> findFirstByUserIdAndTenantIdAndRoleIn(
      UUID userId, UUID tenantId, List<Role> roles);

  long countByRoleIn(List<Role> roles);

  @org.springframework.data.jpa.repository.Query(
      """
      select count(distinct m.tenantId) from MembershipEntity m
      where m.tenantId is not null
        and m.role in :roles
      """)
  long countDistinctTenantsHavingMerchantMembership(@org.springframework.data.repository.query.Param("roles") List<Role> roles);

  long countByRole(Role role);
}

