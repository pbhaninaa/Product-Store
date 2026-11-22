package com.productstore.platform.repositories;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.UserEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  Optional<UserEntity> findByEmailIgnoreCase(String email);

  Optional<UserEntity> findByReferralCodeIgnoreCase(String referralCode);

  long count();

  /**
   * Stamps the last sign-in with a targeted update, so signing in never overwrites a concurrent
   * password change or suspension by saving a stale copy of the whole row.
   */
  @Transactional
  @Modifying
  @Query("update UserEntity u set u.lastLoginAt = :at where u.id = :id")
  int stampLastLogin(@Param("id") UUID id, @Param("at") Instant at);
}

