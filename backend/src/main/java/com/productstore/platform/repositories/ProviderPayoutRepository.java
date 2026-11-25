package com.productstore.platform.repositories;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.ProviderPayoutEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Payout history. Ranges are half open: {@code from <= released_at < to}. */
public interface ProviderPayoutRepository extends JpaRepository<ProviderPayoutEntity, UUID> {

  Page<ProviderPayoutEntity> findByTenantId(UUID tenantId, Pageable page);

  Page<ProviderPayoutEntity> findByReleasedAtGreaterThanEqualAndReleasedAtLessThan(
      Instant from, Instant to, Pageable page);

  Page<ProviderPayoutEntity> findByTenantIdAndReleasedAtGreaterThanEqualAndReleasedAtLessThan(
      UUID tenantId, Instant from, Instant to, Pageable page);

  List<ProviderPayoutEntity> findByTenantIdOrderByReleasedAtDescIdDesc(UUID tenantId);

  List<ProviderPayoutEntity> findByTenantIdAndReleasedAtGreaterThanEqualAndReleasedAtLessThanOrderByReleasedAtDescIdDesc(
      UUID tenantId, Instant from, Instant to);
}
