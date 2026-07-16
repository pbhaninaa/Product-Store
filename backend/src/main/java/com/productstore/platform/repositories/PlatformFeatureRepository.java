package com.productstore.platform.repositories;

import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.PlatformFeatureEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformFeatureRepository extends JpaRepository<PlatformFeatureEntity, UUID> {
  Optional<PlatformFeatureEntity> findByFeatureKey(String featureKey);
}
