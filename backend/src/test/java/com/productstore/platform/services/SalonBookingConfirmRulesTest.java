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
    b.handoverCode = "246810";
    when(bookings.lockById(b.id)).thenReturn(Optional.of(b));
    return b;
  }

  @Test
  void unpaidOnlineBookingCannotBeConfirmedByMerchant() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.pending);

    assertThatThrownBy(() -> service.updateBookingStatus(tenantId, b.id, "confirmed", null, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("in_app_payment_not_received");
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.pending);
    verify(bookings, never()).save(any());
  }

  @Test
  void unpaidOnlineBookingCanStillBeCancelled() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.pending);

    service.updateBookingStatus(tenantId, b.id, "cancelled", null, true);

    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.cancelled);
  }

  @Test
  void paidOnlineBookingMovesForwardToInProgress() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.confirmed);

    service.updateBookingStatus(tenantId, b.id, "in_progress", "246810", true);

    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.in_progress);
  }

  @Test
  void startingAServiceNeedsTheHandoverCode() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.confirmed);

    assertThatThrownBy(() -> service.updateBookingStatus(tenantId, b.id, "in_progress", "  ", true))
        .hasMessage("handover_code_required");
    assertThatThrownBy(() -> service.updateBookingStatus(tenantId, b.id, "in_progress", "111111", true))
        .hasMessage("invalid_handover_code");
    // Skipping the start step still counts as starting.
    assertThatThrownBy(() -> service.updateBookingStatus(tenantId, b.id, "completed", null, true))
        .hasMessage("handover_code_required");
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.confirmed);
    verify(bookings, never()).save(any());

    service.updateBookingStatus(tenantId, b.id, "completed", "246810", true);
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.completed);
  }

  @Test
  void completingAStartedServiceNeedsNoCode() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.in_progress);

    service.updateBookingStatus(tenantId, b.id, "completed", null, true);

    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.completed);
  }

  @Test
  void unpaidCashBookingStartsWithTheCodeAndIsMarkedPaid() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.cash_store, SalonBookingEntity.Status.pending);

    service.updateBookingStatus(tenantId, b.id, "in_progress", "246810", true);

    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.in_progress);
    assertThat(b.completedAt).isNotNull();
  }

  @Test
  void startingAnUnpaidCashBookingNeedsThePaymentPermission() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.cash_store, SalonBookingEntity.Status.pending);

    assertThatThrownBy(() -> service.updateBookingStatus(tenantId, b.id, "in_progress", "246810", false))
        .hasMessage("permission_denied");
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.pending);
    assertThat(b.completedAt).isNull();
  }

  @Test
  void unpaidOnlineBookingCannotBeStartedEvenWithTheCode() {
    SalonBookingEntity b =
        booking(SalonBookingEntity.ClientPaymentMethod.online, SalonBookingEntity.Status.pending);

    assertThatThrownBy(() -> service.updateBookingStatus(tenantId, b.id, "in_progress", "246810", true))
        .hasMessage("invalid_status");
    assertThat(b.status).isEqualTo(SalonBookingEntity.Status.pending);
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
