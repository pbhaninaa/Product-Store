package com.productstore.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.productstore.platform.controllers.PlatformApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the one time migration runs before Hibernate's {@code ddl-auto=update}: an existing database with
 * the old columns ends up with them renamed, not with empty new columns created beside them. Deleted
 * together with {@link LegacyPaymentSchemaMigration}.
 */
@SpringBootTest(
    classes = PlatformApplication.class,
    properties = {
      "spring.datasource.url=" + LegacyPaymentSchemaMigrationStartupTest.URL,
      "spring.jpa.hibernate.ddl-auto=update"
    })
@ActiveProfiles("test")
class LegacyPaymentSchemaMigrationStartupTest {
  static final String URL =
      "jdbc:h2:mem:legacy_startup;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

  static {
    // Runs when the class loads, before Spring creates the context: an old style table with data columns.
    try (Connection c = DriverManager.getConnection(URL, "sa", "");
        Statement s = c.createStatement()) {
      s.execute(
          "CREATE TABLE subscription_peach_payments (id CHAR(36) PRIMARY KEY, tenant_id CHAR(36) NOT NULL,"
              + " plan_tier VARCHAR(16) NOT NULL, amount DECIMAL(12,2) NOT NULL, currency VARCHAR(8) NOT NULL,"
              + " status VARCHAR(24) NOT NULL, peach_payment_method ENUM ('CARD','EFT') NOT NULL,"
              + " peach_merchant_transaction_id VARCHAR(64) NOT NULL, peach_checkout_id VARCHAR(128),"
              + " created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL, completed_at TIMESTAMP(6) WITH TIME ZONE,"
              + " CONSTRAINT uk_subscription_peach_merchant_tx UNIQUE (peach_merchant_transaction_id))");
      s.execute(
          "INSERT INTO subscription_peach_payments (id, tenant_id, plan_tier, amount, currency, status,"
              + " peach_payment_method, peach_merchant_transaction_id, created_at) VALUES"
              + " ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'STANDARD',"
              + " 199.00, 'ZAR', 'pending_peach', 'CARD', 'SUBKEEP', CURRENT_TIMESTAMP)");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Autowired DataSource dataSource;

  @Test
  void oldColumnsAreRenamedBeforeHibernateUpdatesTheSchema() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement()) {
      List<String> tables = new ArrayList<>();
      try (ResultSet rs =
          s.executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = CURRENT_SCHEMA")) {
        while (rs.next()) tables.add(rs.getString(1));
      }
      assertThat(tables).contains("subscription_online_payments").doesNotContain("subscription_peach_payments");

      List<String> columns = new ArrayList<>();
      try (ResultSet rs =
          s.executeQuery(
              "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = CURRENT_SCHEMA"
                  + " AND TABLE_NAME = 'subscription_online_payments'")) {
        while (rs.next()) columns.add(rs.getString(1));
      }
      assertThat(columns).contains("online_payment_reference").noneMatch(col -> col.contains("peach"));

      try (ResultSet rs =
          s.executeQuery("SELECT online_payment_reference, status FROM subscription_online_payments")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("SUBKEEP");
        assertThat(rs.getString(2)).isEqualTo("pending_online");
      }
    }
  }
}
