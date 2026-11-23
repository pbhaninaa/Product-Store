package com.productstore.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Runs the one time migration against an H2 database in MySQL mode (as SIT uses) holding the old schema
 * exactly as Hibernate created it, with data, and checks every rename and value change plus that a
 * second run does nothing. Deleted together with {@link LegacyPaymentSchemaMigration}.
 */
class LegacyPaymentSchemaMigrationTest {
  private DataSource dataSource;

  @BeforeEach
  void freshDatabase() {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.h2.Driver");
    ds.setUrl(
        "jdbc:h2:mem:legacy_" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    ds.setUsername("sa");
    ds.setPassword("");
    dataSource = ds;
  }

  @Test
  void emptyDatabaseIsLeftAlone() throws Exception {
    assertThat(runOnce()).isZero();
    assertThat(tables()).isEmpty();
  }

  @Test
  void renamesOldSchemaKeepsDataAndIsIdempotent() throws Exception {
    createOldSchema();

    assertThat(runOnce()).isPositive();

    // Columns renamed, data kept.
    for (String table : List.of("orders", "salon_bookings", "merchant_subscriptions")) {
      assertThat(columns(table))
          .contains("online_payment_channel", "online_payment_reference", "online_payment_provider_id")
          .doesNotContain("peach_payment_method", "peach_merchant_transaction_id", "peach_checkout_id");
    }
    assertThat(one("SELECT online_payment_reference FROM orders WHERE id = 'o1'")).isEqualTo("REFO1");
    assertThat(one("SELECT online_payment_provider_id FROM orders WHERE id = 'o1'")).isEqualTo("PF123");
    assertThat(one("SELECT online_payment_channel FROM orders WHERE id = 'o1'")).isEqualTo("EFT");
    assertThat(one("SELECT online_payment_reference FROM salon_bookings WHERE id = 'b1'")).isEqualTo("REFB1");
    assertThat(one("SELECT online_payment_channel FROM merchant_subscriptions WHERE id = 's1'")).isEqualTo("CARD");

    // Payment method values moved to online; others untouched.
    assertThat(one("SELECT payment_method FROM orders WHERE id = 'o1'")).isEqualTo("online");
    assertThat(one("SELECT payment_method FROM orders WHERE id = 'o2'")).isEqualTo("online");
    assertThat(one("SELECT payment_method FROM orders WHERE id = 'o3'")).isEqualTo("cash_store");
    assertThat(one("SELECT client_payment_method FROM salon_bookings WHERE id = 'b1'")).isEqualTo("online");
    assertThat(one("SELECT client_payment_method FROM salon_bookings WHERE id = 'b2'")).isEqualTo("eft");
    // The old ENUM no longer blocks the new value.
    exec("INSERT INTO orders (id, payment_method) VALUES ('o4', 'online')");

    // Subscription ledger table, its columns, status values, constraint and indexes.
    assertThat(tables()).contains("subscription_online_payments").doesNotContain("subscription_peach_payments");
    assertThat(columns("subscription_online_payments"))
        .contains("online_payment_channel", "online_payment_reference", "online_payment_provider_id");
    assertThat(one("SELECT status FROM subscription_online_payments WHERE id = 'p1'")).isEqualTo("pending_online");
    assertThat(one("SELECT status FROM subscription_online_payments WHERE id = 'p2'")).isEqualTo("completed");
    assertThat(one("SELECT online_payment_reference FROM subscription_online_payments WHERE id = 'p2'"))
        .isEqualTo("SUBREF2");
    assertThat(indexes("subscription_online_payments"))
        .contains("idx_subscription_online_tenant_status", "idx_subscription_online_checkout")
        .noneMatch(n -> n.contains("peach"));
    assertThat(constraints("subscription_online_payments"))
        .contains("uk_subscription_online_merchant_tx")
        .noneMatch(n -> n.contains("peach"));
    assertThat(indexes("orders")).contains("idx_orders_online_merchant_tx").noneMatch(n -> n.contains("peach"));

    // Shop flag renamed with its values; retired feature row and provider text gone.
    assertThat(columns("shop_settings")).contains("accept_customer_online").doesNotContain("accept_customer_peach");
    assertThat(one("SELECT accept_customer_online FROM shop_settings WHERE id = 'ss1'")).isEqualTo("FALSE");
    assertThat(one("SELECT COUNT(*) FROM platform_features WHERE feature_key = 'PAYFAST'")).isEqualTo("0");
    assertThat(one("SELECT COUNT(*) FROM platform_features WHERE feature_key = 'PAYMENTS'")).isEqualTo("1");
    // The retired switch became PAYSTACK and kept its off state.
    assertThat(one("SELECT enabled FROM platform_features WHERE feature_key = 'PAYSTACK'")).isEqualTo("FALSE");
    assertThat(one("SELECT payment_proof_auto_summary FROM merchant_subscriptions WHERE id = 's1'"))
        .isEqualTo("Paid online.");

    // A second start finds nothing to do and changes nothing.
    assertThat(runOnce()).isZero();
    assertThat(one("SELECT online_payment_reference FROM orders WHERE id = 'o1'")).isEqualTo("REFO1");
  }

  /** A database Hibernate already created with the new model (tests, new environments) needs nothing. */
  @Test
  void schemaAlreadyInTheNewModelIsLeftAlone() throws Exception {
    exec(
        "CREATE TABLE orders (id VARCHAR(36) PRIMARY KEY,"
            + " payment_method ENUM ('cash_store','eft','online') NOT NULL,"
            + " online_payment_channel ENUM ('CARD','EFT'), online_payment_reference VARCHAR(64))");
    exec(
        "CREATE TABLE salon_bookings (id VARCHAR(36) PRIMARY KEY,"
            + " client_payment_method ENUM ('cash_store','eft','online'))");
    exec("INSERT INTO orders (id, payment_method) VALUES ('o1', 'online')");
    exec("INSERT INTO salon_bookings (id, client_payment_method) VALUES ('b1', 'eft')");

    assertThat(runOnce()).isZero();
    assertThat(one("SELECT payment_method FROM orders WHERE id = 'o1'")).isEqualTo("online");
  }

  @Test
  void retiredFeatureRowIsRemovedWhenPaystackRowAlreadyExists() throws Exception {
    exec("CREATE TABLE platform_features (id VARCHAR(36) PRIMARY KEY, feature_key VARCHAR(64) NOT NULL, enabled BOOLEAN)");
    exec("INSERT INTO platform_features VALUES ('f1', 'PAYFAST', FALSE), ('f2', 'PAYSTACK', TRUE)");

    assertThat(runOnce()).isEqualTo(1);

    assertThat(one("SELECT COUNT(*) FROM platform_features WHERE feature_key = 'PAYFAST'")).isEqualTo("0");
    assertThat(one("SELECT enabled FROM platform_features WHERE feature_key = 'PAYSTACK'")).isEqualTo("TRUE");
    assertThat(runOnce()).isZero();
  }

  @Test
  void dropsOldCheckConstraintOnVarcharColumns() throws Exception {
    // Older Hibernate versions made enum columns VARCHAR with a CHECK listing the allowed values.
    exec(
        "CREATE TABLE orders (id VARCHAR(36) PRIMARY KEY, payment_method VARCHAR(255) NOT NULL"
            + " CHECK (payment_method IN ('eft','cash_store','payfast','peach')))");
    exec("INSERT INTO orders (id, payment_method) VALUES ('o1', 'peach')");

    assertThat(runOnce()).isPositive();

    assertThat(one("SELECT payment_method FROM orders WHERE id = 'o1'")).isEqualTo("online");
    exec("INSERT INTO orders (id, payment_method) VALUES ('o2', 'online')");
    assertThat(runOnce()).isZero();
  }

  @Test
  void addsOnlineFlagSwitchedOnWhenShopSettingsPredatesIt() throws Exception {
    exec("CREATE TABLE shop_settings (id VARCHAR(36) PRIMARY KEY, accept_customer_eft BOOLEAN NOT NULL)");
    exec("INSERT INTO shop_settings (id, accept_customer_eft) VALUES ('ss1', TRUE)");

    assertThat(runOnce()).isEqualTo(1);

    assertThat(one("SELECT accept_customer_online FROM shop_settings WHERE id = 'ss1'")).isEqualTo("TRUE");
    assertThat(runOnce()).isZero();
  }

  @Test
  void copiesValuesWhenOldAndNewColumnsBothExist() throws Exception {
    exec(
        "CREATE TABLE orders (id VARCHAR(36) PRIMARY KEY, payment_method VARCHAR(32),"
            + " peach_merchant_transaction_id VARCHAR(64), online_payment_reference VARCHAR(64))");
    exec("INSERT INTO orders (id, payment_method, peach_merchant_transaction_id) VALUES ('o1', 'online', 'OLD1')");

    runOnce();

    assertThat(one("SELECT online_payment_reference FROM orders WHERE id = 'o1'")).isEqualTo("OLD1");
  }

  // ------------------------------------------------------------------ helpers

  /** The schema as Hibernate 6 and the old startup runner created it on H2, with one row of each kind. */
  private void createOldSchema() throws SQLException {
    exec(
        "CREATE TABLE orders (id VARCHAR(36) PRIMARY KEY,"
            + " payment_method ENUM ('cash_store','eft','payfast','peach') NOT NULL,"
            + " peach_payment_method ENUM ('CARD','EFT'),"
            + " peach_merchant_transaction_id VARCHAR(64), peach_checkout_id VARCHAR(128))");
    exec("CREATE INDEX idx_orders_peach_merchant_tx ON orders (peach_merchant_transaction_id)");
    exec(
        "INSERT INTO orders VALUES ('o1', 'payfast', 'EFT', 'REFO1', 'PF123'),"
            + " ('o2', 'peach', 'CARD', NULL, NULL), ('o3', 'cash_store', NULL, NULL, NULL)");

    exec(
        "CREATE TABLE salon_bookings (id VARCHAR(36) PRIMARY KEY,"
            + " client_payment_method ENUM ('cash_store','eft','payfast','peach'),"
            + " peach_payment_method ENUM ('CARD','EFT'),"
            + " peach_merchant_transaction_id VARCHAR(64), peach_checkout_id VARCHAR(128))");
    exec("INSERT INTO salon_bookings VALUES ('b1', 'payfast', 'CARD', 'REFB1', NULL), ('b2', 'eft', NULL, NULL, NULL)");

    exec(
        "CREATE TABLE merchant_subscriptions (id VARCHAR(36) PRIMARY KEY,"
            + " peach_payment_method ENUM ('CARD','EFT'), peach_merchant_transaction_id VARCHAR(64),"
            + " peach_checkout_id VARCHAR(128), payment_proof_auto_summary VARCHAR(2000))");
    exec("INSERT INTO merchant_subscriptions VALUES ('s1', 'CARD', 'SUBREF2', 'PF9', 'Paid online via PayFast.')");

    exec(
        "CREATE TABLE subscription_peach_payments (id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(36) NOT NULL,"
            + " status VARCHAR(24) NOT NULL, peach_payment_method ENUM ('CARD','EFT') NOT NULL,"
            + " peach_merchant_transaction_id VARCHAR(64) NOT NULL, peach_checkout_id VARCHAR(128),"
            + " CONSTRAINT uk_subscription_peach_merchant_tx UNIQUE (peach_merchant_transaction_id))");
    exec("CREATE INDEX idx_subscription_peach_tenant_status ON subscription_peach_payments (tenant_id, status)");
    exec("CREATE INDEX idx_subscription_peach_checkout ON subscription_peach_payments (peach_checkout_id)");
    exec(
        "INSERT INTO subscription_peach_payments VALUES ('p1', 't1', 'pending_peach', 'EFT', 'SUBREF1', NULL),"
            + " ('p2', 't1', 'completed', 'CARD', 'SUBREF2', 'PF9')");

    exec(
        "CREATE TABLE shop_settings (id VARCHAR(36) PRIMARY KEY, accept_customer_peach BOOLEAN NOT NULL,"
            + " accept_customer_eft BOOLEAN NOT NULL)");
    exec("INSERT INTO shop_settings VALUES ('ss1', FALSE, TRUE)");

    exec(
        "CREATE TABLE platform_features (id VARCHAR(36) PRIMARY KEY, feature_key VARCHAR(64) NOT NULL,"
            + " enabled BOOLEAN NOT NULL, description VARCHAR(240))");
    exec("INSERT INTO platform_features VALUES ('f1', 'PAYFAST', FALSE, 'old'), ('f2', 'PAYMENTS', TRUE, 'p')");
  }

  private int runOnce() throws SQLException {
    try (Connection c = dataSource.getConnection()) {
      return new LegacyPaymentSchemaMigration.Run(c, LegacyPaymentSchemaMigration.detect(c)).migrate();
    }
  }

  private void exec(String sql) throws SQLException {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private String one(String sql) throws SQLException {
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      return rs.next() ? rs.getString(1) : null;
    }
  }

  private List<String> list(String sql) throws SQLException {
    List<String> out = new ArrayList<>();
    try (Connection c = dataSource.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(sql)) {
      while (rs.next()) out.add(rs.getString(1).toLowerCase());
    }
    return out;
  }

  private List<String> tables() throws SQLException {
    return list("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = CURRENT_SCHEMA");
  }

  private List<String> columns(String table) throws SQLException {
    return list(
        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = CURRENT_SCHEMA AND TABLE_NAME = '"
            + table + "'");
  }

  private List<String> indexes(String table) throws SQLException {
    return list(
        "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_SCHEMA = CURRENT_SCHEMA AND TABLE_NAME = '"
            + table + "'");
  }

  private List<String> constraints(String table) throws SQLException {
    return list(
        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = CURRENT_SCHEMA"
            + " AND TABLE_NAME = '" + table + "'");
  }
}
