package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.models.PayoutDtos;
import com.productstore.platform.repositories.OnlinePaymentAttemptRepository;
import com.productstore.platform.repositories.ProviderPayoutLineRepository;
import com.productstore.platform.repositories.ProviderPayoutRepository;
import com.productstore.platform.services.PlatformCommissionService;
import com.productstore.platform.services.ProviderPayoutService;
import com.productstore.platform.services.SitDangerZoneService;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/** AC-20: the test-database reset removes payouts and their payment links and keeps the commission. */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderPayoutResetIntegrationTest {

  @Autowired ApplicationContext ctx;
  @Autowired PlatformCommissionService commission;
  @Autowired ProviderPayoutService payoutService;
  @Autowired SitDangerZoneService dangerZone;
  @Autowired ProviderPayoutRepository payouts;
  @Autowired ProviderPayoutLineRepository payoutLines;
  @Autowired OnlinePaymentAttemptRepository attempts;

  PayoutFixtures.User admin;

  @AfterEach
  void resetCommission() {
    commission.set("0", admin.principal());
  }

  @Test
  void resetRemovesPayoutsAndTheirPaymentLinksAndKeepsTheCommission() {
    PayoutFixtures fx = new PayoutFixtures(ctx);
    admin = fx.user(Role.PLATFORM_ADMIN, null);
    commission.set("7.5", admin.principal());
    TenantEntity m = fx.merchant("Reset Salon");
    fx.bookingPayment(m.id, 10_000, 100L);
    fx.orderPayment(
        m.id, 20_000, null, com.productstore.platform.entities.OrderEntity.OrderStatus.paid,
        com.productstore.platform.entities.OnlinePaymentAttemptEntity.Outcome.PAID, java.time.Instant.now());
    PayoutDtos.ReleasePreview preview = payoutService.preview(m.id);
    payoutService.release(
        new ProviderPayoutService.ReleaseRequest(
            m.id, preview.payments().stream().map(PayoutDtos.Line::attemptId).toList(), preview.payableVersion(), null,
            "RESET-REF", null),
        admin.principal());
    assertThat(payouts.count()).isPositive();
    assertThat(payoutLines.count()).isPositive();

    dangerZone.resetRecords(admin.principal(), SitDangerZoneService.CONFIRM_PHRASE);

    assertThat(payouts.count()).isZero();
    assertThat(payoutLines.count()).isZero();
    assertThat(attempts.count()).isZero();
    assertThat(commission.currentBasisPoints()).isEqualTo(750);
  }
}
