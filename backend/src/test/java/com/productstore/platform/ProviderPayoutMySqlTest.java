package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.productstore.platform.controllers.PlatformApplication;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.models.PayoutDtos;
import com.productstore.platform.repositories.ProviderPayoutLineRepository;
import com.productstore.platform.repositories.ProviderPayoutRepository;
import com.productstore.platform.services.PlatformCommissionService;
import com.productstore.platform.services.ProviderPayoutService;
import com.productstore.platform.services.auth.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Provider payouts on a real MySQL 8 (UAT/PROD engine) with {@code ddl-auto=update}: the tables, the unique
 * payment key and the amount check are created and enforced, and two concurrent releases of one merchant
 * create exactly one payout (ADR-0001). Skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
class ProviderPayoutMySqlTest {

  @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

  @DynamicPropertySource
  static void mysql(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
  }

  @Autowired DataSource dataSource;
  @Autowired ApplicationContext ctx;
  @Autowired PlatformCommissionService commission;
  @Autowired ProviderPayoutService payoutService;
  @Autowired ProviderPayoutRepository payouts;
  @Autowired ProviderPayoutLineRepository payoutLines;

  @Test
  void schemaHasTheUniquePaymentKeyAndTheAmountCheck() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement()) {
      assertThat(list(s, "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()"))
          .contains("provider_payouts", "provider_payout_lines", "platform_commission_settings");
      assertThat(
              list(
                  s,
                  "SELECT COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                      + " AND TABLE_NAME = 'provider_payout_lines' AND INDEX_NAME = 'uk_provider_payout_lines_attempt'"
                      + " AND NON_UNIQUE = 0"))
          .containsExactly("payment_attempt_id");
      assertThat(
              list(
                  s,
                  "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                      + " AND TABLE_NAME = 'online_payment_attempts' AND COLUMN_NAME = 'fee_cents' AND IS_NULLABLE = 'YES'"))
          .containsExactly("fee_cents");
      assertThat(
              list(
                  s,
                  "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
                      + " AND TABLE_NAME = 'online_payment_attempts'"))
          .contains("idx_online_payment_attempts_tenant_outcome");

      String tenant = UUID.randomUUID().toString();
      String admin = UUID.randomUUID().toString();
      // net != gross - fee - commission: refused by the check constraint.
      assertThatThrownBy(() -> s.executeUpdate(payoutInsert(tenant, admin, 1000, 100, 100, 900)))
          .isInstanceOf(SQLException.class);
      // net <= 0: refused.
      assertThatThrownBy(() -> s.executeUpdate(payoutInsert(tenant, admin, 100, 50, 50, 0)))
          .isInstanceOf(SQLException.class);
      assertThat(s.executeUpdate(payoutInsert(tenant, admin, 1000, 100, 100, 800))).isEqualTo(1);
    }
  }

  @Test
  void concurrentReleasesCreateExactlyOnePayout() throws Exception {
    PayoutFixtures fx = new PayoutFixtures(ctx);
    PayoutFixtures.User one = fx.user(Role.PLATFORM_ADMIN, null);
    PayoutFixtures.User two = fx.user(Role.PLATFORM_ADMIN, null);
    commission.set("10", one.principal());
    TenantEntity m = fx.merchant("MySQL Race Salon");
    for (int i = 0; i < 5; i++) fx.bookingPayment(m.id, 10_000 + i, 100L);
    PayoutDtos.ReleasePreview preview = payoutService.preview(m.id);
    List<UUID> ids = preview.payments().stream().map(PayoutDtos.Line::attemptId).toList();

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Object> results = new ArrayList<>();
    try {
      List<Future<Object>> futures = new ArrayList<>();
      for (PayoutFixtures.User u : List.of(one, two)) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  try {
                    return (Object)
                        payoutService.release(
                            new ProviderPayoutService.ReleaseRequest(
                                m.id, ids, preview.payableVersion(), null, "RACE", null),
                            u.principal());
                  } catch (RuntimeException e) {
                    return e;
                  }
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      for (Future<Object> f : futures) results.add(f.get(60, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }

    assertThat(results).filteredOn(r -> r instanceof PayoutDtos.PayoutDetail).hasSize(1);
    assertThat(results)
        .filteredOn(r -> r instanceof IllegalStateException e && "payout_already_released".equals(e.getMessage()))
        .hasSize(1);
    assertThat(payouts.findByTenantIdOrderByReleasedAtDescIdDesc(m.id)).hasSize(1);
    assertThat(payoutLines.findReleasedAttemptIds(ids)).hasSize(5).doesNotHaveDuplicates();
  }

  /** The read-only query in the plan (§13 Reconciliation) runs on MySQL and lists only pre-ledger payments. */
  @Test
  void reconciliationQueryListsPaidOnlinePaymentsWithoutAnAttempt() throws Exception {
    PayoutFixtures fx = new PayoutFixtures(ctx);
    TenantEntity m = fx.merchant("Legacy Salon");
    var withAttempt = fx.orderPayment(
        m.id, 10_000, 100L, com.productstore.platform.entities.OrderEntity.OrderStatus.paid,
        com.productstore.platform.entities.OnlinePaymentAttemptEntity.Outcome.PAID, java.time.Instant.now());
    var legacy = fx.orderPayment(
        m.id, 20_000, null, com.productstore.platform.entities.OrderEntity.OrderStatus.paid,
        com.productstore.platform.entities.OnlinePaymentAttemptEntity.Outcome.PAID, java.time.Instant.now());
    var legacyBooking = fx.bookingPayment(
        m.id, 30_000, null, com.productstore.platform.entities.SalonBookingEntity.Status.completed,
        com.productstore.platform.entities.OnlinePaymentAttemptEntity.Outcome.PAID, java.time.Instant.now());
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement()) {
      // Make the two "legacy" ones look like payments from before the ledger: no attempt row.
      s.executeUpdate("DELETE FROM online_payment_attempts WHERE id IN ('" + legacy.id + "', '" + legacyBooking.id + "')");

      String plan = java.nio.file.Files.readString(java.nio.file.Path.of("../docs/engineering/plans/provider-payouts.md"));
      String section = plan.substring(plan.indexOf("### 13. Reconciliation"));
      String sql = section.substring(section.indexOf("```sql") + 6, section.indexOf("```", section.indexOf("```sql") + 6));
      sql = sql.replaceAll("(?m)--.*$", "").trim();
      if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1);

      List<String> recordIds = new ArrayList<>();
      try (ResultSet rs = s.executeQuery(sql)) {
        while (rs.next()) {
          if (m.id.toString().equals(rs.getString("tenant_id"))) recordIds.add(rs.getString("record_id"));
        }
      }
      assertThat(recordIds).containsExactlyInAnyOrder(legacy.recordId.toString(), legacyBooking.recordId.toString());
      assertThat(recordIds).doesNotContain(withAttempt.recordId.toString());
    }
  }

  private static String payoutInsert(String tenant, String admin, long gross, long fee, long commission, long net) {
    return "INSERT INTO provider_payouts (id, tenant_id, payment_count, gross_cents, captured_fee_cents,"
        + " unknown_fee_count, fee_cents, fee_adjusted, commission_basis_points, commission_cents, net_cents,"
        + " reference, released_by_user_id, released_by_email, released_at) VALUES ('" + UUID.randomUUID() + "', '"
        + tenant + "', 1, " + gross + ", " + fee + ", 0, " + fee + ", b'0', 1000, " + commission + ", " + net
        + ", 'REF', '" + admin + "', 'a@test.local', NOW(6))";
  }

  private static List<String> list(Statement s, String sql) throws Exception {
    List<String> out = new ArrayList<>();
    try (ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) out.add(rs.getString(1));
    }
    return out;
  }
}
