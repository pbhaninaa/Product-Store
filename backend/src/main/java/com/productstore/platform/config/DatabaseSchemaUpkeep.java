package com.productstore.platform.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Idempotent schema upkeep for existing MySQL databases that Hibernate's {@code ddl-auto=update} gets
 * wrong: NOT NULL columns whose existing rows need a default other than Hibernate's implicit one (Hibernate
 * would add {@code accept_customer_eft} switched off for every existing shop).
 *
 * <p>Runs <em>before</em> Hibernate builds the EntityManagerFactory (see {@link RunBeforeJpa}); after it,
 * Hibernate would already have added the columns and these defaults would never apply. Tables that do not
 * exist yet (fresh database) are left for Hibernate. Problems are logged and startup continues: every column
 * here is also created by Hibernate, only with a less suitable default.
 *
 * <p>Flyway is intentionally inactive in this app; the {@code db/migration} files are reference only.
 */
@Component(DatabaseSchemaUpkeep.BEAN_NAME)
public class DatabaseSchemaUpkeep implements InitializingBean {
  static final String BEAN_NAME = "databaseSchemaUpkeep";

  private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaUpkeep.class);

  /** Makes the EntityManagerFactory (and so Hibernate's schema update) wait for this upkeep. */
  @Component
  static class RunBeforeJpa extends EntityManagerFactoryDependsOnPostProcessor {
    RunBeforeJpa() {
      super(BEAN_NAME);
    }
  }

  private final DataSource dataSource;

  public DatabaseSchemaUpkeep(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void afterPropertiesSet() {
    try (Connection c = dataSource.getConnection()) {
      String product = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
      if (!product.contains("mysql")) {
        return;
      }
      int added = ensureShopSettingsPaymentFlags(c) + ensureMerchantTrialColumns(c);
      if (added > 0) {
        log.info("Schema upkeep added {} column(s)", added);
      }
    } catch (SQLException e) {
      log.warn("Could not ensure schema columns: {}", e.getMessage());
    }
  }

  /** Customer manual EFT is on by default for shops created before the flag existed. */
  static int ensureShopSettingsPaymentFlags(Connection c) throws SQLException {
    if (!tableExists(c, "shop_settings")) {
      return 0;
    }
    return addColumnIfMissing(c, "shop_settings", "accept_customer_eft", "TINYINT(1) NOT NULL DEFAULT 1");
  }

  /** Durable merchant free trial columns (V14 reference DDL). */
  static int ensureMerchantTrialColumns(Connection c) throws SQLException {
    if (!tableExists(c, "merchant_subscriptions")) {
      return 0;
    }
    return addColumnIfMissing(c, "merchant_subscriptions", "trial_start_at", "TIMESTAMP(6) NULL")
        + addColumnIfMissing(c, "merchant_subscriptions", "trial_end_at", "TIMESTAMP(6) NULL")
        + addColumnIfMissing(
            c, "merchant_subscriptions", "trial_dates_backfilled", "TINYINT(1) NOT NULL DEFAULT 0");
  }

  private static int addColumnIfMissing(Connection c, String table, String column, String sqlType)
      throws SQLException {
    if (columnExists(c, table, column)) {
      return 0;
    }
    try (Statement s = c.createStatement()) {
      s.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + sqlType);
      log.info("Added {}.{}", table, column);
    }
    return 1;
  }

  private static boolean tableExists(Connection c, String table) throws SQLException {
    return count(
            c,
            "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
            table)
        > 0;
  }

  private static boolean columnExists(Connection c, String table, String column) throws SQLException {
    return count(
            c,
            "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND TABLE_NAME = ? AND COLUMN_NAME = ?",
            table,
            column)
        > 0;
  }

  private static long count(Connection c, String sql, String... params) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setString(i + 1, params[i]);
      }
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getLong(1) : 0;
      }
    }
  }
}
