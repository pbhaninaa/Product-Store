package com.productstore.platform.repositories;

import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.PasswordResetTokenEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {
  Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

  @Modifying(clearAutomatically = true)
  @Query("delete from PasswordResetTokenEntity t where t.userId = :userId and t.usedAt is null")
  int deleteUnusedByUserId(@Param("userId") UUID userId);
}
