package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.ShopSettingsEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShopSettingsRepository extends JpaRepository<ShopSettingsEntity, UUID> {
  Optional<ShopSettingsEntity> findByTenantId(UUID tenantId);

  @Query(
      """
      select s from ShopSettingsEntity s
      where s.storeLat is not null
        and s.storeLng is not null
      """)
  List<ShopSettingsEntity> findAllWithStoreCoordinates();
}

