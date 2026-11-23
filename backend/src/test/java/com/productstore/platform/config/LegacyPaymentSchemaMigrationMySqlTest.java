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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The one time migration against a real MySQL 8 holding the old schema as Hibernate made it there (native
 * ENUM columns, the explicit and the hash named unique key, the old startup runner's indexes) with rows:
 * migrate twice, then let the app start with {@code ddl-auto=update} and check nothing was duplicated.
 * Also proves {@link DatabaseSchemaUpkeep} runs before Hibernate (its column default applies).
 *
 * <p>Skipped when Docker is not available. Deleted together with {@link LegacyPaymentSchemaMigration}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
class LegacyPaymentSchemaMigrationMySqlTest {

  @Container
  static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

  @DynamicPropertySource
  static void mysql(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
  }

  /** Runs after the container starts and before Spring builds the context (and so before Hibernate). */
  @BeforeAll
  static void oldSchemaMigratedTwice() throws Exception {
    try (Connection c = connect();
        Statement s = c.createStatement()) {
      s.execute(
          "CREATE TABLE orders (id CHAR(36) PRIMARY KEY, tenant_id CHAR(36) NOT NULL,"
              + " payment_method ENUM('cash_store','eft','payfast','peach') NOT NULL,"
              + " peach_payment_method ENUM('CARD','EFT'),"
              + " peach_merchant_transaction_id VARCHAR(64), peach_checkout_id VARCHAR(128),"
              + " status ENUM('cancelled','paid','pending_payment') NOT NULL)");
      s.execute("CREATE INDEX idx_orders_peach_merchant_tx ON orders (peach_merchant_transaction_id)");
      s.execute("CREATE INDEX idx_orders_peach_checkout ON orders (peach_checkout_id)");
      s.execute(
          "INSERT INTO orders VALUES"
              + " ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000f1', 'payfast',"
              + " 'EFT', 'REFO1', 'PF1', 'paid'),"
              + " ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-0000000000f1', 'cash_store',"
              + " NULL, NULL, NULL, 'pending_payment')");

      s.execute(
          "CREATE TABLE salon_bookings (id CHAR(36) PRIMARY KEY, tenant_id CHAR(36) NOT NULL,"
              + " client_payment_method ENUM('cash_store','eft','payfast','peach'),"
              + " peach_payment_method ENUM('CARD','EFT'),"
              + " peach_merchant_transaction_id VARCHAR(64), peach_checkout_id VARCHAR(128))");
      s.execute(
          "INSERT INTO salon_bookings VALUES ('00000000-0000-0000-0000-0000000000b1',"
              + " '00000000-0000-0000-0000-0000000000f1', 'peach', 'CARD', 'REFB1', NULL)");

      s.execute(
          "CREATE TABLE subscription_peach_payments (id CHAR(36) NOT NULL PRIMARY KEY,"
              + " tenant_id CHAR(36) NOT NULL, plan_tier VARCHAR(16) NOT NULL, amount DECIMAL(12,2) NOT NULL,"
              + " currency VARCHAR(8) NOT NULL DEFAULT 'ZAR', status VARCHAR(24) NOT NULL,"
              + " peach_payment_method ENUM('CARD','EFT') NOT NULL,"
              + " peach_merchant_transaction_id VARCHAR(64) NOT NULL, peach_checkout_id VARCHAR(128) NULL,"
              + " created_at DATETIME(6) NOT NULL, completed_at DATETIME(6) NULL,"
              + " CONSTRAINT uk_subscription_peach_merchant_tx UNIQUE (peach_merchant_transaction_id),"
              + " CONSTRAINT UKq8w3e5r7t9y1u3i5o7p9a1s3d UNIQUE (peach_merchant_transaction_id))");
      s.execute("CREATE INDEX idx_subscription_peach_tenant_status ON subscription_peach_payments (tenant_id, status)");
      s.execute("CREATE INDEX idx_subscription_peach_checkout ON subscription_peach_payments (peach_checkout_id)");
      s.execute(
          "INSERT INTO subscription_peach_payments (id, tenant_id, plan_tier, amount, status,"
              + " peach_payment_method, peach_merchant_transaction_id, created_at) VALUES"
              + " ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000f1', 'STANDARD',"
              + " 199.00, 'pending_peach', 'EFT', 'SUBREF1', NOW(6))");

      s.execute(
          "CREATE TABLE merchant_subscriptions (id CHAR(36) PRIMARY KEY, tenant_id CHAR(36) NOT NULL UNIQUE,"
              + " peach_payment_method ENUM('CARD','EFT'), peach_merchant_transaction_id VARCHAR(64),"
              + " peach_checkout_id VARCHAR(128), payment_proof_auto_summary VARCHAR(2000))");
      s.execute("CREATE INDEX idx_merchant_subscriptions_peach_merchant_tx ON merchant_subscriptions (peach_merchant_transaction_id)");
      s.execute("CREATE INDEX idx_merchant_subscriptions_peach_checkout ON merchant_subscriptions (peach_checkout_id)");
      s.execute(
          "INSERT INTO merchant_subscriptions VALUES ('00000000-0000-0000-0000-0000000000b9',"
              + " '00000000-0000-0000-0000-0000000000f1', 'EFT', 'SUBREF1', 'PF77', 'Paid online via PayFast.')");

      // Predates the manual EFT flag: DatabaseSchemaUpkeep must add it switched on before Hibernate does.
      s.execute(
          "CREATE TABLE shop_settings (id CHAR(36) PRIMARY KEY, tenant_id CHAR(36) NOT NULL,"
              + " accept_customer_peach BIT NOT NULL)");
      s.execute(
          "INSERT INTO shop_settings VALUES ('00000000-0000-0000-0000-0000000000d1',"
              + " '00000000-0000-0000-0000-0000000000f1', b'0')");

      s.execute(
          "CREATE TABLE platform_features (id CHAR(36) PRIMARY KEY, feature_key VARCHAR(64) NOT NULL,"
              + " enabled BIT NOT NULL, description VARCHAR(240), updated_at DATETIME(6) NOT NULL,"
              + " UNIQUE (feature_key))");
      s.execute(
          "INSERT INTO platform_features VALUES ('00000000-0000-0000-0000-0000000000e1', 'PAYFAST', b'0',"
              + " 'old', NOW(6))");
    }

    try (Connection c = connect()) {
      assertThat(new LegacyPaymentSchemaMigration.Run(c, LegacyPaymentSchemaMigration.detect(c)).migrate()).isPositive();
    }
    try (Connection c = connect()) {
      assertThat(new LegacyPaymentSchemaMigration.Run(c, LegacyPaymentSchemaMigration.detect(c)).migrate()).isZero();
    }
  }

  @Autowired DataSource dataSource;

  @Test
  void hibernateUpdateAfterMigrationKeepsDataAndDuplicatesNothing() throws Exception {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement()) {
      // Merchant subscriptions: columns renamed with their values, summary text neutral, indexes renamed once.
      String sub = "'00000000-0000-0000-0000-0000000000b9'";
      assertThat(one(s, "SELECT online_payment_reference FROM merchant_subscriptions WHERE id = " + sub)).isEqualTo("SUBREF1");
      assertThat(one(s, "SELECT online_payment_provider_id FROM merchant_subscriptions WHERE id = " + sub)).isEqualTo("PF77");
      assertThat(one(s, "SELECT online_payment_channel FROM merchant_subscriptions WHERE id = " + sub)).isEqualTo("EFT");
      assertThat(one(s, "SELECT payment_proof_auto_summary FROM merchant_subscriptions WHERE id = " + sub))
          .isEqualTo("Paid online.");
      assertThat(indexesOn(s, "merchant_subscriptions", "online_payment_reference"))
          .containsExactly("idx_merchant_subscriptions_online_merchant_tx");

      for (String table :
          List.of("orders", "salon_bookings", "subscription_online_payments", "shop_settings", "merchant_subscriptions")) {
        assertThat(columns(s, table)).noneMatch(col -> col.contains("peach"));
      }
      assertThat(tables(s)).contains("subscription_online_payments").doesNotContain("subscription_peach_payments");

      // Data kept, values moved, and the old ENUM no longer blocks "online".
      assertThat(one(s, "SELECT payment_method FROM orders WHERE id = '00000000-0000-0000-0000-0000000000a1'"))
          .isEqualTo("online");
      assertThat(one(s, "SELECT online_payment_reference FROM orders WHERE id = '00000000-0000-0000-0000-0000000000a1'"))
          .isEqualTo("REFO1");
      assertThat(one(s, "SELECT online_payment_channel FROM orders WHERE id = '00000000-0000-0000-0000-0000000000a1'"))
          .isEqualTo("EFT");
      assertThat(one(s, "SELECT payment_method FROM orders WHERE id = '00000000-0000-0000-0000-0000000000a2'"))
          .isEqualTo("cash_store");
      assertThat(one(s, "SELECT client_payment_method FROM salon_bookings WHERE id = '00000000-0000-0000-0000-0000000000b1'"))
          .isEqualTo("online");
      assertThat(one(s, "SELECT status FROM subscription_online_payments")).isEqualTo("pending_online");
      assertThat(one(s, "SELECT online_payment_reference FROM subscription_online_payments")).isEqualTo("SUBREF1");

      // One index per looked-up column: Hibernate found the renamed ones and added none of its own.
      assertThat(indexesOn(s, "orders", "online_payment_reference")).containsExactly("idx_orders_online_merchant_tx");
      assertThat(indexesOn(s, "orders", "online_payment_provider_id")).containsExactly("idx_orders_online_checkout");
      assertThat(indexesOn(s, "subscription_online_payments", "online_payment_reference"))
          .contains("uk_subscription_online_merchant_tx")
          .hasSize(2); // the renamed explicit key plus the old hash named one, nothing new
      assertThat(indexNames(s, "subscription_online_payments")).noneMatch(n -> n.toLowerCase().contains("peach"));

      // Shop flags: online renamed with its value, manual EFT added switched on (upkeep ran before Hibernate).
      assertThat(one(s, "SELECT CAST(accept_customer_online AS UNSIGNED) FROM shop_settings")).isEqualTo("0");
      assertThat(one(s, "SELECT CAST(accept_customer_eft AS UNSIGNED) FROM shop_settings")).isEqualTo("1");

      // The retired feature switch is now PAYSTACK and stayed off.
      assertThat(one(s, "SELECT CAST(enabled AS UNSIGNED) FROM platform_features WHERE feature_key = 'PAYSTACK'"))
          .isEqualTo("0");
      assertThat(one(s, "SELECT COUNT(*) FROM platform_features WHERE feature_key = 'PAYFAST'")).isEqualTo("0");

      s.executeUpdate("UPDATE orders SET payment_method = 'online' WHERE id = '00000000-0000-0000-0000-0000000000a2'");
    }
  }

  private static Connection connect() throws Exception {
    return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
  }

  private static String one(Statement s, String sql) throws Exception {
    try (ResultSet rs = s.executeQuery(sql)) {
      return rs.next() ? rs.getString(1) : null;
    }
  }

  private static List<String> list(Statement s, String sql) throws Exception {
    List<String> out = new ArrayList<>();
    try (ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) out.add(rs.getString(1));
    }
    return out;
  }

  private static List<String> tables(Statement s) throws Exception {
    return list(s, "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()");
  }

  private static List<String> columns(Statement s, String table) throws Exception {
    return list(
        s,
        "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '"
            + table + "'");
  }

  private static List<String> indexNames(Statement s, String table) throws Exception {
    return list(
        s,
        "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
            + " AND TABLE_NAME = '" + table + "'");
  }

  /** Indexes whose first column is {@code column}. */
  private static List<String> indexesOn(Statement s, String table, String column) throws Exception {
    return list(
        s,
        "SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()"
            + " AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "' AND SEQ_IN_INDEX = 1");
  }
}
