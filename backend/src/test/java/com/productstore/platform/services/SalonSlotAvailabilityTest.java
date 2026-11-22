package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.SalonServiceEntity;
import com.productstore.platform.entities.SalonStaffAvailabilityEntity;
import com.productstore.platform.entities.SalonStaffEntity;
import com.productstore.platform.repositories.SalonBookingRepository;
import com.productstore.platform.repositories.SalonServiceRepository;
import com.productstore.platform.repositories.SalonStaffAvailabilityRepository;
import com.productstore.platform.repositories.SalonStaffRepository;
import com.productstore.platform.repositories.ShopSettingsRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Slots already started are hidden; fully booked slots stay in the list, marked full. */
class SalonSlotAvailabilityTest {

  private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
  private static final LocalDate DAY = LocalDate.of(2026, 11, 23); // a Monday
  /** "Now" is 12:15 on that day. */
  private static final Instant NOW = ZonedDateTime.of(DAY, LocalTime.of(12, 15), SAST).toInstant();

  private final UUID tenantId = UUID.randomUUID();
  private final UUID serviceId = UUID.randomUUID();
  private final UUID staffId = UUID.randomUUID();
  private SalonBookingRepository bookings;
  private SalonBookingService service;

  @BeforeEach
  void setUp() {
    SalonServiceRepository services = mock(SalonServiceRepository.class);
    SalonStaffRepository staff = mock(SalonStaffRepository.class);
    SalonStaffAvailabilityRepository availability = mock(SalonStaffAvailabilityRepository.class);
    ShopOpeningHoursService openingHours = mock(ShopOpeningHoursService.class);
    bookings = mock(SalonBookingRepository.class);

    SalonServiceEntity svc = new SalonServiceEntity();
    svc.id = serviceId;
    svc.tenantId = tenantId;
    svc.durationMinutes = 30;
    svc.active = true;
    when(services.findByIdAndTenantId(serviceId, tenantId)).thenReturn(Optional.of(svc));

    SalonStaffEntity member = new SalonStaffEntity();
    member.id = staffId;
    member.active = true;
    when(staff.findActiveByTenant(tenantId)).thenReturn(List.of(member));

    // One staff member working 09:00-17:00.
    SalonStaffAvailabilityEntity window = new SalonStaffAvailabilityEntity();
    window.staffId = staffId;
    window.dayOfWeek = 1;
    window.startTime = LocalTime.of(9, 0);
    window.endTime = LocalTime.of(17, 0);
    when(availability.findForStaffOnDay(eq(tenantId), anyList(), anyInt())).thenReturn(List.of(window));
    when(openingHours.intersectStaffDayBounds(eq(tenantId), anyInt(), any(), any()))
        .thenAnswer(inv -> Optional.of(new LocalTime[] {inv.getArgument(2), inv.getArgument(3)}));
    when(bookings.findOverlapping(eq(tenantId), any(), any(), anyList())).thenReturn(List.of());

    StaticListableBeanFactory beans =
        new StaticListableBeanFactory(Map.of("clock", Clock.fixed(NOW, SAST)));
    service =
        new SalonBookingService(
            services,
            staff,
            availability,
            bookings,
            openingHours,
            mock(ShopSettingsRepository.class),
            mock(EftProofDocumentAnalyzer.class),
            mock(MerchantNotificationService.class),
            mock(PromotionService.class),
            "./build/test-uploads",
            "http://localhost:8080",
            beans.getBeanProvider(Clock.class));
  }

  private static Instant at(int hour, int minute) {
    return ZonedDateTime.of(DAY, LocalTime.of(hour, minute), SAST).toInstant();
  }

  @Test
  void slotsThatHaveAlreadyStartedAreNotOffered() {
    List<SalonBookingService.Slot> slots = service.getAvailableSlots(tenantId, serviceId, DAY);

    assertThat(slots).isNotEmpty();
    assertThat(slots).allMatch(s -> s.startAt().isAfter(NOW));
    // 12:15 itself has started; the first offered time is 12:30.
    assertThat(slots.get(0).startAt()).isEqualTo(at(12, 30));
  }

  @Test
  void fullyBookedSlotIsReturnedMarkedFull() {
    SalonBookingEntity taken = new SalonBookingEntity();
    taken.startAt = at(14, 0);
    taken.endAt = at(14, 30);
    taken.status = SalonBookingEntity.Status.confirmed;
    when(bookings.findOverlapping(eq(tenantId), any(), any(), anyList())).thenReturn(List.of(taken));

    List<SalonBookingService.Slot> slots = service.getAvailableSlots(tenantId, serviceId, DAY);

    SalonBookingService.Slot twoPm =
        slots.stream().filter(s -> s.startAt().equals(at(14, 0))).findFirst().orElseThrow();
    assertThat(twoPm.full()).isTrue();
    SalonBookingService.Slot threePm =
        slots.stream().filter(s -> s.startAt().equals(at(15, 0))).findFirst().orElseThrow();
    assertThat(threePm.full()).isFalse();
  }

  @Test
  void bookingATimeThatHasPassedIsRejected() {
    assertThatThrownBy(
            () ->
                service.createBooking(
                    tenantId, serviceId, "Thandi", "0821234567", "thandi@example.com",
                    at(9, 0), "cash", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("slot_in_past");
  }
}
