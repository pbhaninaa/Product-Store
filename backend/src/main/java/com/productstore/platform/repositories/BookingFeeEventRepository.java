package com.productstore.platform.repositories;

import java.util.List;
import java.util.UUID;

import com.productstore.platform.entities.BookingFeeEventEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingFeeEventRepository extends JpaRepository<BookingFeeEventEntity, UUID> {

  List<BookingFeeEventEntity> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
