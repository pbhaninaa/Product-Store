package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.constants.SubscriptionPaymentProofStatus;
import com.productstore.platform.entities.MerchantSubscriptionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantSubscriptionRepository extends JpaRepository<MerchantSubscriptionEntity, UUID> {
  Optional<MerchantSubscriptionEntity> findByTenantId(UUID tenantId);

  List<MerchantSubscriptionEntity> findByPaymentProofStatus(SubscriptionPaymentProofStatus status);

  long countByPaymentProofStatus(SubscriptionPaymentProofStatus status);

  MerchantSubscriptionEntity findFirstByPeachMerchantTransactionId(String peachMerchantTransactionId);

  MerchantSubscriptionEntity findFirstByPeachCheckoutId(String peachCheckoutId);
}
