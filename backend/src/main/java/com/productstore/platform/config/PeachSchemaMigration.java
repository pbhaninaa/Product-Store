package com.productstore.platform.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Idempotent Peach schema upgrades for existing MySQL/MariaDB databases.
 *
 * <p>Flyway is intentionally inactive in this app ({@code spring.jpa.hibernate.ddl-auto=update}).
 * Nullable Peach columns are also covered by Hibernate, but renames and NOT NULL defaults are not —
 * this runner makes deploy safe without manual SQL.
 */
@Component
@Order(1)
public class PeachSchemaMigration implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(PeachSchemaMigration.class);

  private final DataSource dataSource;

  public PeachSchemaMigration(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void run(ApplicationArguments args) {
    try (Connection c = dataSource.getConnection()) {
      String url = c.getMetaData().getURL().toLowerCase();
      if (!url.contains("mysql") && !url.contains("mariadb")) {
        return;
      }
      migrateShopSettingsPaymentFlag(c);
      ensurePeachColumns(c, "orders");
      ensurePeachColumns(c, "salon_bookings");
      ensurePeachColumns(c, "merchant_subscriptions");
      ensureMerchantTrialColumns(c);
      ensureIndex(c, "orders", "idx_orders_peach_merchant_tx", "peach_merchant_transaction_id");
      ensureIndex(c, "orders", "idx_orders_peach_checkout", "peach_checkout_id");
      ensureIndex(c, "salon_bookings", "idx_bookings_peach_merchant_tx", "peach_merchant_transaction_id");
      ensureIndex(c, "salon_bookings", "idx_bookings_peach_checkout", "peach_checkout_id");
      ensureIndex(
          c,
          "merchant_subscriptions",
          "idx_merchant_subscriptions_peach_merchant_tx",
          "peach_merchant_transaction_id");
      ensureIndex(
          c, "merchant_subscriptions", "idx_merchant_subscriptions_peach_checkout", "peach_checkout_id");
      ensureSubscriptionPeachPaymentsTable(c);
    } catch (SQLException e) {
      log.warn("Could not ensure Peach schema columns: {}", e.getMessage());
    }
  }

  private static void ensureSubscriptionPeachPaymentsTable(Connection c) throws SQLException {
    if (tableExists(c, "subscription_peach_payments")) {
      ensureIndex(
          c,
          "subscription_peach_payments",
          "idx_subscription_peach_tenant_status",
          "tenant_id");
      ensureIndex(
          c, "subscription_peach_payments", "idx_subscription_peach_checkout", "peach_checkout_id");
      return;
    }
    try (Statement s = c.createStatement()) {
      s.execute(
          "CREATE TABLE subscription_peach_payments ("
              + "id CHAR(36) NOT NULL PRIMARY KEY,"
              + "tenant_id CHAR(36) NOT NULL,"
              + "plan_tier VARCHAR(16) NOT NULL,"
              + "amount DECIMAL(12,2) NOT NULL,"
              + "currency VARCHAR(8) NOT NULL DEFAULT 'ZAR',"
              + "status VARCHAR(24) NOT NULL,"
              + "peach_payment_method VARCHAR(16) NOT NULL,"
              + "peach_merchant_transaction_id VARCHAR(64) NOT NULL,"
              + "peach_checkout_id VARCHAR(128) NULL,"
              + "created_at TIMESTAMP(6) NOT NULL,"
              + "completed_at TIMESTAMP(6) NULL,"
              + "CONSTRAINT uk_subscription_peach_merchant_tx UNIQUE (peach_merchant_transaction_id)"
              + ")");
      log.info("Created subscription_peach_payments");
    }
    ensureIndex(
        c, "subscription_peach_payments", "idx_subscription_peach_tenant_status", "tenant_id");
    ensureIndex(
        c, "subscription_peach_payments", "idx_subscription_peach_checkout", "peach_checkout_id");
  }

  private static void migrateShopSettingsPaymentFlag(Connection c) throws SQLException {
    boolean hasPeach = columnExists(c, "shop_settings", "accept_customer_peach");
    boolean hasEft = columnExists(c, "shop_settings", "accept_customer_eft");
    if (hasPeach) {
      return;
    }
    try (Statement s = c.createStatement()) {
      if (hasEft) {
        s.execute(
            "ALTER TABLE shop_settings CHANGE COLUMN accept_customer_eft accept_customer_peach "
                + "TINYINT(1) NOT NULL DEFAULT 1");
        log.info("Renamed shop_settings.accept_customer_eft → accept_customer_peach");
      } else if (tableExists(c, "shop_settings")) {
        s.execute(
            "ALTER TABLE shop_settings ADD COLUMN accept_customer_peach TINYINT(1) NOT NULL DEFAULT 1");
        log.info("Added shop_settings.accept_customer_peach");
      }
    }
  }

  private static void ensurePeachColumns(Connection c, String table) throws SQLException {
    if (!tableExists(c, table)) {
      return;
    }
    addColumnIfMissing(c, table, "peach_checkout_id", "VARCHAR(128) NULL");
    addColumnIfMissing(c, table, "peach_merchant_transaction_id", "VARCHAR(64) NULL");
    addColumnIfMissing(c, table, "peach_payment_method", "VARCHAR(16) NULL");
  }

  /** Durable merchant free-trial columns (V14 reference DDL). Idempotent on every startup. */
  private static void ensureMerchantTrialColumns(Connection c) throws SQLException {
    if (!tableExists(c, "merchant_subscriptions")) {
      return;
    }
    addColumnIfMissing(c, "merchant_subscriptions", "trial_start_at", "TIMESTAMP(6) NULL");
    addColumnIfMissing(c, "merchant_subscriptions", "trial_end_at", "TIMESTAMP(6) NULL");
    addColumnIfMissing(
        c, "merchant_subscriptions", "trial_dates_backfilled", "TINYINT(1) NOT NULL DEFAULT 0");
  }

  private static void addColumnIfMissing(Connection c, String table, String column, String sqlType)
      throws SQLException {
    if (columnExists(c, table, column)) {
      return;
    }
    try (Statement s = c.createStatement()) {
      s.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + sqlType);
      log.info("Added {}.{}", table, column);
    }
  }

  private static void ensureIndex(Connection c, String table, String indexName, String column)
      throws SQLException {
    if (!tableExists(c, table) || !columnExists(c, table, column) || indexExists(c, table, indexName)) {
      return;
    }
    try (Statement s = c.createStatement()) {
      s.execute("CREATE INDEX " + indexName + " ON " + table + " (" + column + ")");
      log.info("Created index {} on {}.{}", indexName, table, column);
    } catch (SQLException e) {
      // Concurrent startup or dialect differences — column lookup already makes this idempotent.
      log.debug("Index {} on {} not created: {}", indexName, table, e.getMessage());
    }
  }

  private static boolean tableExists(Connection c, String table) throws SQLException {
    try (Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '"
                    + table
                    + "'")) {
      return rs.next() && rs.getInt(1) > 0;
    }
  }

  private static boolean columnExists(Connection c, String table, String column) throws SQLException {
    try (Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '"
                    + table
                    + "' AND COLUMN_NAME = '"
                    + column
                    + "'")) {
      return rs.next() && rs.getInt(1) > 0;
    }
  }

  private static boolean indexExists(Connection c, String table, String indexName) throws SQLException {
    try (Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '"
                    + table
                    + "' AND INDEX_NAME = '"
                    + indexName
                    + "'")) {
      return rs.next() && rs.getInt(1) > 0;
    }
  }
}
