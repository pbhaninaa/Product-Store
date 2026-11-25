package com.productstore.platform.repositories;

import java.util.UUID;

import com.productstore.platform.entities.PlatformCommissionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformCommissionRepository extends JpaRepository<PlatformCommissionEntity, UUID> {}
