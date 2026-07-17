package com.productstore.platform.repositories;

import java.util.UUID;

import com.productstore.platform.entities.PlatformHelpContactEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformHelpContactRepository extends JpaRepository<PlatformHelpContactEntity, UUID> {}
