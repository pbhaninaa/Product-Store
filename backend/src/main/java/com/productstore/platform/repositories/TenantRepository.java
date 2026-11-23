package com.productstore.platform.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.TenantEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
  Optional<TenantEntity> findBySlug(String slug);

  /**
   * {@code SELECT ... FOR UPDATE} on the merchant row: serialises provider payout releases per merchant.
   * Must be the first statement of the release transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from TenantEntity t where t.id = :id")
  Optional<TenantEntity> lockById(@Param("id") UUID id);

  @Query(
      """
      select t from TenantEntity t
      where :q is null or :q = ''
        or lower(t.slug) like lower(concat('%', :q, '%'))
        or lower(t.name) like lower(concat('%', :q, '%'))
      order by t.createdAt desc
      """)
  List<TenantEntity> searchMerchants(@Param("q") String q);
}

