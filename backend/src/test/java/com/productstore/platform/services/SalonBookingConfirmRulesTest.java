package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Merchant "Confirm booking" must not bypass payment for online bookings. */
class SalonBookingConfirmRulesTest {

  private SalonBookingRepository bookings;
  private SalonBookingService service;
  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    bookings = mock(SalonBookingRepository.class);
    service =
        new SalonBookingService(
            mock(SalonServiceRepository.class),
            mock(SalonStaffRepository.class),
            mock(SalonStaffAvailabilityRepository.class),
            bookings,
            mock(ShopOpeningHoursService.class),
            mock(ShopSettingsRepository.class),
            mock(EftProofDocumentAnalyzer.class),
            mock(MerchantNotificationService.class),
            mock(PromotionService.class),
            mock(OnlinePaymentAvailability.class),
            "./build/test-uploads",
            "http://localhost:8080",
            new org.springframework.beans.factory.support.StaticListableBeanFactory()
                .getBeanProvider(java.time.Clock.class));
  }

  private SalonBookingEntity booking(
      SalonBookingEntity.ClientPaymentMethod method, SalonBookingEntity.Status status) {
    SalonBookingEntity b = new SalonBookingEntity();
    b.id = UUID.randomUUID();
    b.tenantId = tenantId;
    b.serviceId = UUID.randomUUID();
    b.customerName = "Test";
    b.startAt = Instant.now().plusSeconds(3600);
    b.endAt = b.startAt.plusSeconds(1800);
    b.status = status;
    b.clientPaymentMethod = method;
    b.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.not_applicable;
    when(bookings.lockById(b.id)).thenReturn(Optional.of(b));
    return b;
  }

  @Test
  void unpaidOnlineBookingCannotBeConfirmedByMerchant() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.pending);

    assertThatThrownBy(() -> service.updateBookingStatus(tenantId, b.id, "confirmed"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("in_app_payment_not_received");
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.pending);
    verify(bookings, never()).save(any());
  }

  @Test
  void unpaidOnlineBookingCanStillBeCancelled() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.pending);

    service.updateBookingStatus(tenantId, b.id, "cancelled");

    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.cancelled);
  }

  @Test
  void paidOnlineBookingMovesForwardToInProgress() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.confirmed);

    service.updateBookingStatus(tenantId, b.id, "in_progress");

    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.in_progress);
  }

  @Test
  void blocksQuickConfirmOnlyWhileInAppBookingIsPending() {
    SalonBookingEntity pending = new SalonBookingEntity();
    pending.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.online;
    pending.status = SalonBookingEntity.Status.pending;
    assertThat(SalonBookingService.blocksMerchantQuickConfirm(pending)).isTrue();

    SalonBookingEntity confirmed = new SalonBookingEntity();
    confirmed.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.online;
    confirmed.status = SalonBookingEntity.Status.confirmed;
    assertThat(SalonBookingService.blocksMerchantQuickConfirm(confirmed)).isFalse();

    SalonBookingEntity eftVerified = new SalonBookingEntity();
    eftVerified.clientPaymentMethod = SalonBookingEntity.ClientPaymentMethod.eft;
    eftVerified.status = SalonBookingEntity.Status.pending;
    eftVerified.paymentVerificationState = SalonBookingEntity.PaymentVerificationState.manual_approved;
    assertThat(SalonBookingService.blocksMerchantQuickConfirm(eftVerified)).isFalse();
  }
}
