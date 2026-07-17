package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.SubscriptionPeachPaymentEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionPeachPaymentRepository
    extends JpaRepository<SubscriptionPeachPaymentEntity, UUID> {

  List<SubscriptionPeachPaymentEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select p from SubscriptionPeachPaymentEntity p where p.peachMerchantTransactionId = :reference")
  Optional<SubscriptionPeachPaymentEntity> findByPeachMerchantTransactionIdForUpdate(
      @Param("reference") String reference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from SubscriptionPeachPaymentEntity p where p.peachCheckoutId = :checkoutId")
  Optional<SubscriptionPeachPaymentEntity> findByPeachCheckoutIdForUpdate(
      @Param("checkoutId") String checkoutId);
}
