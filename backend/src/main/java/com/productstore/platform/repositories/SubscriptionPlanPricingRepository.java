package com.productstore.platform.repositories;

import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.SubscriptionPlanPricingEntity;
import com.productstore.platform.entities.TenantEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanPricingRepository
    extends JpaRepository<SubscriptionPlanPricingEntity, UUID> {
  Optional<SubscriptionPlanPricingEntity> findByTier(TenantEntity.SubscriptionPlan tier);
}
