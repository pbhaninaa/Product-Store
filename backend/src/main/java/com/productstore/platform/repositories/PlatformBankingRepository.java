package com.productstore.platform.repositories;

import java.util.UUID;

import com.productstore.platform.entities.PlatformBankingEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformBankingRepository extends JpaRepository<PlatformBankingEntity, UUID> {}
