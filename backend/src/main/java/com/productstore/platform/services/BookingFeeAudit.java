package com.productstore.platform.services;

import java.time.Instant;

import com.productstore.platform.entities.BookingFeeEventEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.repositories.BookingFeeEventRepository;
import com.productstore.platform.util.RefCodes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records the booking fee audit trail (NFR-3) in the caller's transaction, so an event commits or rolls back with
 * the change it describes. Logs carry short references only, never personal data.
 */
@Component
public class BookingFeeAudit {
  private static final Logger log = LoggerFactory.getLogger(BookingFeeAudit.class);
  private static final int DETAIL_MAX = 255;

  private final BookingFeeEventRepository events;

  public BookingFeeAudit(BookingFeeEventRepository events) {
    this.events = events;
  }

  public void record(
      SalonBookingEntity b,
      BookingFeeEventEntity.Type type,
      SalonBookingEntity.BookingFeeReason reason,
      long amountCents,
      String paymentReference,
      String detail,
      Instant at) {
    BookingFeeEventEntity e = new BookingFeeEventEntity();
    e.tenantId = b.tenantId;
    e.bookingId = b.id;
    e.type = type;
    e.reason = reason;
    e.amountCents = amountCents;
    e.paymentReference = paymentReference;
    e.detail = detail == null ? null : (detail.length() > DETAIL_MAX ? detail.substring(0, DETAIL_MAX) : detail);
    e.createdAt = at == null ? Instant.now() : at;
    events.save(e);
    log.info(
        "Booking fee {} for booking {}: {} cents{}{}",
        type,
        RefCodes.shortRef(b.id),
        amountCents,
        reason == null ? "" : " (" + reason + ")",
        paymentReference == null ? "" : " reference " + paymentReference);
  }
}
