package com.productstore.platform.repositories;

import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.ShopSettingsEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingsRepository extends JpaRepository<ShopSettingsEntity, UUID> {
  Optional<ShopSettingsEntity> findByTenantId(UUID tenantId);
}

