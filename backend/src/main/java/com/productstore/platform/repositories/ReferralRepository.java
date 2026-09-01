package com.productstore.platform.repositories;

import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.ReferralEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralRepository extends JpaRepository<ReferralEntity, UUID> {
  Optional<ReferralEntity> findByRefereeId(UUID refereeId);

  Page<ReferralEntity> findByReferrerId(UUID referrerId, Pageable pageable);

  long countByHasSubscribed(boolean hasSubscribed);

  long countByCommissionPaid(boolean commissionPaid);

  long countByHasSubscribedAndCommissionPaid(boolean hasSubscribed, boolean commissionPaid);
}
