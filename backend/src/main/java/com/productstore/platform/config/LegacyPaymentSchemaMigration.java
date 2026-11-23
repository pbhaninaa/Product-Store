package com.productstore.platform.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.stereotype.Component;

/**
 * ONE TIME migration from the retired Peach Payments / PayFast data model to the provider neutral
 * "online payment" model used with Paystack. It renames the old columns, table and indexes in place and
 * migrates old values, keeping every row.
 *
 * <p><b>Delete this class (and its test) in a follow up release once SIT, UAT and PROD have all started
 * with it.</b> It is the only place allowed to name Peach or PayFast, because it must name the old columns.
 *
 * <p>Runs <em>before</em> Hibernate builds the EntityManagerFactory (see {@link RunBeforeJpa}). That
 * matters: with {@code ddl-auto=update} Hibernate would otherwise create empty {@code online_*} columns
 * next to the old ones before any {@code ApplicationRunner} could rename them.
 *
 * <p>Every step first checks the live schema, so it is idempotent: a second start finds nothing to do, and
 * a fresh or empty database (tests, new environments) is left alone for Hibernate to create. Supported:
 * MySQL 8 (UAT, PROD) and H2 in MySQL mode (SIT, tests). Any other database, MariaDB included, is skipped.
 *
 * <p>Failure policy: any SQL error stops startup. MySQL DDL cannot be rolled back, so continuing after a
 * failed rename would let Hibernate add empty new columns beside the old ones and split the data. Stopping
 * keeps the schema in a state the next start resumes from, because each finished step is detected and
 * skipped. Logs name tables, columns and row counts only, never data values.
 */
@Component(LegacyPaymentSchemaMigration.BEAN_NAME)
public class LegacyPaymentSchemaMigration implements InitializingBean {
  static final String BEAN_NAME = "legacyPaymentSchemaMigration";

  private static final Logger log = LoggerFactory.getLogger(LegacyPaymentSchemaMigration.class);
  private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");

  /** Makes the EntityManagerFactory (and so Hibernate's schema update) wait for this migration. */
  @Component
  static class RunBeforeJpa extends EntityManagerFactoryDependsOnPostProcessor {
    RunBeforeJpa() {
      super(BEAN_NAME);
    }
  }

  enum Db {
    MYSQL("DATABASE()"),
    H2("CURRENT_SCHEMA");

    final String schemaExpr;

    Db(String schemaExpr) {
      this.schemaExpr = schemaExpr;
    }
  }

  private static final String[] PAYMENT_TABLES = {"orders", "salon_bookings", "merchant_subscriptions"};
  private static final String OLD_SUB_TABLE = "subscription_peach_payments";
  private static final String NEW_SUB_TABLE = "subscription_online_payments";

  /** Old to new column names, the same on every payment table. */
  private static final String[][] PAYMENT_COLUMNS = {
    {"peach_payment_method", "online_payment_channel"},
    {"peach_merchant_transaction_id", "online_payment_reference"},
    {"peach_checkout_id", "online_payment_provider_id"}
  };

  /** Stored subscription summaries that named the old providers, and their neutral text. */
  private static final String[][] AUTO_SUMMARIES = {
    {"Paid online via PayFast.", "Paid online."},
    {"Paid online via Peach Payments.", "Paid online."},
    {
      "Preferred plan saved during free trial. Peach payment is only required after the trial ends.",
      "Preferred plan saved during free trial. Online payment is only required after the trial ends."
    }
  };

  private final DataSource dataSource;

  public LegacyPaymentSchemaMigration(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void afterPropertiesSet() {
    try (Connection c = dataSource.getConnection()) {
      Db db = detect(c);
      if (db == null) {
        log.info("Legacy payment schema migration skipped: unsupported database {}", c.getMetaData().getDatabaseProductName());
        return;
      }
      int changes = new Run(c, db).migrate();
      if (changes == 0) {
        log.info("Legacy payment schema migration: nothing to do");
      } else {
        log.info("Legacy payment schema migration finished: {} change(s)", changes);
      }
    } catch (SQLException | RuntimeException e) {
      log.error("Legacy payment schema migration failed; startup stopped. Fix the cause and restart: the migration resumes where it stopped.", e);
      throw new IllegalStateException("Legacy payment schema migration failed", e);
    }
  }

  static Db detect(Connection c) throws SQLException {
    String product = c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    if (product.contains("mysql")) return Db.MYSQL;
    if (product.contains("h2")) return Db.H2;
    return null;
  }

  /** One migration pass over one connection. Package visible for tests. */
  static final class Run {
    private final Connection c;
    private final Db db;

    Run(Connection c, Db db) {
      this.c = c;
      this.db = db;
    }

    int migrate() throws SQLException {
      int changes = 0;
      for (String table : PAYMENT_TABLES) {
        changes += renamePaymentColumns(table);
      }
      changes += renameSubscriptionTable();
      changes += renamePaymentColumns(NEW_SUB_TABLE);
      for (String table : PAYMENT_TABLES) {
        changes += renameLegacyIndexes(table);
      }
      changes += renameLegacyIndexes(NEW_SUB_TABLE);
      changes += migrateShopSettingsFlag();
      changes += migrateMethodValues("orders", "payment_method");
      changes += migrateMethodValues("salon_bookings", "client_payment_method");
      changes += updateValue(NEW_SUB_TABLE, "status", "pending_peach", "pending_online");
      for (String[] s : AUTO_SUMMARIES) {
        changes += updateValue("merchant_subscriptions", "payment_proof_auto_summary", s[0], s[1]);
      }
      changes += migrateFeatureRow();
      return changes;
    }

    // ---------------------------------------------------------------- columns and table

    private int renamePaymentColumns(String table) throws SQLException {
      int changes = 0;
      for (String[] col : PAYMENT_COLUMNS) {
        changes += renameColumn(table, col[0], col[1]);
      }
      return changes;
    }

    int renameColumn(String table, String oldName, String newName) throws SQLException {
      if (!tableExists(table) || !columnExists(table, oldName)) {
        return 0;
      }
      if (!columnExists(table, newName)) {
        execute(
            db == Db.MYSQL
                ? "ALTER TABLE " + table + " RENAME COLUMN " + oldName + " TO " + newName
                : "ALTER TABLE " + table + " ALTER COLUMN " + oldName + " RENAME TO " + newName);
        log.info("Renamed column {}.{} to {}", table, oldName, newName);
        return 1;
      }
      // Both exist (for example an older build ran after this migration): keep every value in the new
      // column and leave the old one for a person to drop, since dropping data needs approval.
      int copied =
          update(
              "UPDATE " + table + " SET " + newName + " = " + oldName
                  + " WHERE " + newName + " IS NULL AND " + oldName + " IS NOT NULL");
      log.warn(
          "Both {}.{} and {}.{} exist; copied {} value(s) into the new column. Drop the old column by hand once checked.",
          table, oldName, table, newName, copied);
      return copied > 0 ? 1 : 0;
    }

    private int renameSubscriptionTable() throws SQLException {
      if (!tableExists(OLD_SUB_TABLE)) {
        return 0;
      }
      if (!tableExists(NEW_SUB_TABLE)) {
        execute(
            db == Db.MYSQL
                ? "RENAME TABLE " + OLD_SUB_TABLE + " TO " + NEW_SUB_TABLE
                : "ALTER TABLE " + OLD_SUB_TABLE + " RENAME TO " + NEW_SUB_TABLE);
        log.info("Renamed table {} to {}", OLD_SUB_TABLE, NEW_SUB_TABLE);
        return 1;
      }
      long oldRows = count("SELECT COUNT(*) FROM " + OLD_SUB_TABLE);
      if (oldRows == 0) {
        log.warn("Both {} and {} exist and the old table is empty; left in place. Drop it by hand once checked.", OLD_SUB_TABLE, NEW_SUB_TABLE);
        return 0;
      }
      throw new IllegalStateException(
          "Both " + OLD_SUB_TABLE + " and " + NEW_SUB_TABLE + " exist and the old table has " + oldRows
              + " row(s). Merge them by hand before starting this version.");
    }

    // ---------------------------------------------------------------- indexes and constraints

    /** Renames indexes (and on H2 named constraints) whose name contains "peach" to the same name with "online". */
    int renameLegacyIndexes(String table) throws SQLException {
      if (!tableExists(table)) {
        return 0;
      }
      int changes = 0;
      if (db == Db.H2) {
        for (String name :
            names(
                "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = "
                    + db.schemaExpr + " AND LOWER(TABLE_NAME) = ?",
                table)) {
          if (!isLegacyName(name)) continue;
          String target = onlineName(name);
          execute("ALTER TABLE " + table + " RENAME CONSTRAINT " + quote(name) + " TO " + quote(target));
          log.info("Renamed constraint {} on {} to {}", name, table, target);
          changes++;
        }
      }
      String indexQuery =
          db == Db.MYSQL
              ? "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = ?"
              : "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_SCHEMA = CURRENT_SCHEMA AND LOWER(TABLE_NAME) = ?";
      List<String> indexes = names(indexQuery, table);
      for (String name : indexes) {
        if (!isLegacyName(name)) continue;
        String target = onlineName(name);
        if (indexes.stream().anyMatch(n -> n.equalsIgnoreCase(target))) {
          log.warn("Index {} on {} not renamed: {} already exists", name, table, target);
          continue;
        }
        execute(
            db == Db.MYSQL
                ? "ALTER TABLE " + table + " RENAME INDEX " + quote(name) + " TO " + quote(target)
                : "ALTER INDEX " + quote(name) + " RENAME TO " + quote(target));
        log.info("Renamed index {} on {} to {}", name, table, target);
        changes++;
      }
      return changes;
    }

    private static boolean isLegacyName(String name) {
      return name != null && name.toLowerCase(Locale.ROOT).contains("peach") && IDENTIFIER.matcher(name).matches();
    }

    private static String onlineName(String name) {
      return name.replace("PEACH", "ONLINE").replaceAll("(?i)peach", "online");
    }

    /** Names read from the information schema keep their stored case (H2 upper cases generated names). */
    private String quote(String identifier) {
      return db == Db.MYSQL ? "`" + identifier + "`" : "\"" + identifier + "\"";
    }

    // ---------------------------------------------------------------- shop settings flag

    private int migrateShopSettingsFlag() throws SQLException {
      if (!tableExists("shop_settings")) {
        return 0;
      }
      int changes = renameColumn("shop_settings", "accept_customer_peach", "accept_customer_online");
      if (!columnExists("shop_settings", "accept_customer_online")) {
        // Databases from before online payment: add the flag switched on, as Hibernate would add it off.
        execute(
            db == Db.MYSQL
                ? "ALTER TABLE shop_settings ADD COLUMN accept_customer_online TINYINT(1) NOT NULL DEFAULT 1"
                : "ALTER TABLE shop_settings ADD COLUMN accept_customer_online BOOLEAN DEFAULT TRUE NOT NULL");
        log.info("Added shop_settings.accept_customer_online");
        changes++;
      }
      return changes;
    }

    // ---------------------------------------------------------------- payment method values

    /**
     * Moves {@code peach}/{@code payfast} to {@code online}. Hibernate made these columns native ENUMs (or,
     * in older versions, VARCHAR with a CHECK listing the values), which reject {@code online}; such a
     * column becomes VARCHAR(32), keeping its NULL / NOT NULL, before the values are updated.
     */
    int migrateMethodValues(String table, String column) throws SQLException {
      if (!tableExists(table) || !columnExists(table, column)) {
        return 0;
      }
      int changes = dropLegacyChecks(table);
      List<String> enumValues = enumValues(table, column);
      if (enumValues != null && !enumValues.contains("online")) {
        boolean nullable = isNullable(table, column);
        execute(
            db == Db.MYSQL
                ? "ALTER TABLE " + table + " MODIFY COLUMN " + column + " VARCHAR(32) " + (nullable ? "NULL" : "NOT NULL")
                : "ALTER TABLE " + table + " ALTER COLUMN " + column + " SET DATA TYPE VARCHAR(32)");
        log.info("Changed {}.{} from ENUM to VARCHAR(32) so it accepts online", table, column);
        changes++;
      } else if (enumValues != null && !enumValues.contains("peach") && !enumValues.contains("payfast")) {
        // Already the new ENUM (fresh schema): it cannot hold the old values, and H2 rejects even comparing
        // such a column with a value outside the ENUM.
        return changes;
      }
      int rows =
          update(
              "UPDATE " + table + " SET " + column + " = 'online' WHERE " + column + " IN ('peach', 'payfast')");
      if (rows > 0) {
        log.info("Set {}.{} to online on {} row(s)", table, column, rows);
        changes++;
      }
      return changes;
    }

    private int dropLegacyChecks(String table) throws SQLException {
      List<String[]> checks = new ArrayList<>();
      String sql =
          "SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc"
              + " JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA"
              + " AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME"
              + " WHERE tc.TABLE_SCHEMA = " + db.schemaExpr + " AND LOWER(tc.TABLE_NAME) = ?"
              + " AND tc.CONSTRAINT_TYPE = 'CHECK'";
      try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, table);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            checks.add(new String[] {rs.getString(1), rs.getString(2)});
          }
        }
      } catch (SQLException e) {
        if (db == Db.MYSQL) {
          // MySQL before 8.0.16 has no CHECK_CONSTRAINTS view and does not enforce checks either.
          log.debug("No CHECK constraint lookup on {}: {}", table, e.getMessage());
          return 0;
        }
        throw e;
      }
      int dropped = 0;
      for (String[] check : checks) {
        String clause = check[1] == null ? "" : check[1].toLowerCase(Locale.ROOT);
        if (!(clause.contains("'payfast'") || clause.contains("'peach'")) || !IDENTIFIER.matcher(check[0]).matches()) {
          continue;
        }
        execute(
            db == Db.MYSQL
                ? "ALTER TABLE " + table + " DROP CHECK " + quote(check[0])
                : "ALTER TABLE " + table + " DROP CONSTRAINT " + quote(check[0]));
        log.info("Dropped CHECK constraint {} on {} that listed the old payment values", check[0], table);
        dropped++;
      }
      return dropped;
    }

    /** The allowed values when the column is a native ENUM, otherwise null. */
    private List<String> enumValues(String table, String column) throws SQLException {
      List<String> out = new ArrayList<>();
      if (db == Db.MYSQL) {
        String sql =
            "SELECT DATA_TYPE, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()"
                + " AND LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
          ps.setString(1, table);
          ps.setString(2, column);
          try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next() || !"enum".equalsIgnoreCase(rs.getString(1))) return null;
            // COLUMN_TYPE looks like enum('cash_store','eft','payfast','peach')
            java.util.regex.Matcher m =
                Pattern.compile("'([^']*)'").matcher(rs.getString(2) == null ? "" : rs.getString(2));
            while (m.find()) out.add(m.group(1).toLowerCase(Locale.ROOT));
            return out;
          }
        }
      }
      String sql =
          "SELECT DATA_TYPE, DTD_IDENTIFIER FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = CURRENT_SCHEMA"
              + " AND LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?";
      String dtd;
      try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, table);
        ps.setString(2, column);
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next() || !"ENUM".equalsIgnoreCase(rs.getString(1))) return null;
          dtd = rs.getString(2);
        }
      }
      String values =
          "SELECT VALUE_NAME FROM INFORMATION_SCHEMA.ENUM_VALUES WHERE OBJECT_SCHEMA = CURRENT_SCHEMA"
              + " AND LOWER(OBJECT_NAME) = ? AND OBJECT_TYPE = 'TABLE' AND ENUM_IDENTIFIER = ?";
      try (PreparedStatement ps = c.prepareStatement(values)) {
        ps.setString(1, table);
        ps.setString(2, dtd);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            out.add(rs.getString(1).toLowerCase(Locale.ROOT));
          }
        }
      }
      return out;
    }

    private boolean isNullable(String table, String column) throws SQLException {
      String sql =
          "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = " + db.schemaExpr
              + " AND LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?";
      try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, table);
        ps.setString(2, column);
        try (ResultSet rs = ps.executeQuery()) {
          return rs.next() && "YES".equalsIgnoreCase(rs.getString(1));
        }
      }
    }

    // ---------------------------------------------------------------- other values

    private int updateValue(String table, String column, String oldValue, String newValue) throws SQLException {
      if (!tableExists(table) || !columnExists(table, column)) {
        return 0;
      }
      int rows;
      try (PreparedStatement ps =
          c.prepareStatement("UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?")) {
        ps.setString(1, newValue);
        ps.setString(2, oldValue);
        rows = ps.executeUpdate();
      }
      if (rows > 0) {
        log.info("Updated {}.{} on {} row(s)", table, column, rows);
        return 1;
      }
      return 0;
    }

    /**
     * The retired provider's platform feature switch becomes the PAYSTACK switch, keeping its on/off state, so
     * a platform admin who had switched online payment off does not find it back on. When a PAYSTACK row
     * already exists, the old row is just removed.
     */
    private int migrateFeatureRow() throws SQLException {
      if (!tableExists("platform_features") || !columnExists("platform_features", "feature_key")) {
        return 0;
      }
      if (count("SELECT COUNT(*) FROM platform_features WHERE feature_key = ?", "PAYFAST") == 0) {
        return 0;
      }
      if (count("SELECT COUNT(*) FROM platform_features WHERE feature_key = ?", "PAYSTACK") == 0) {
        boolean hasDescription = columnExists("platform_features", "description");
        String sql =
            "UPDATE platform_features SET feature_key = ?"
                + (hasDescription ? ", description = ?" : "")
                + " WHERE feature_key = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
          int i = 1;
          ps.setString(i++, "PAYSTACK");
          if (hasDescription) ps.setString(i++, "Paystack online payments (card and Instant EFT)");
          ps.setString(i, "PAYFAST");
          ps.executeUpdate();
        }
        log.info("Renamed the retired platform_features row to PAYSTACK, keeping its on/off state");
        return 1;
      }
      int rows;
      try (PreparedStatement ps = c.prepareStatement("DELETE FROM platform_features WHERE feature_key = ?")) {
        ps.setString(1, "PAYFAST");
        rows = ps.executeUpdate();
      }
      log.info("Removed {} retired row(s) from platform_features (PAYSTACK already present)", rows);
      return rows > 0 ? 1 : 0;
    }

    // ---------------------------------------------------------------- schema lookups

    boolean tableExists(String table) throws SQLException {
      return count(
              "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = " + db.schemaExpr
                  + " AND LOWER(TABLE_NAME) = ?",
              table)
          > 0;
    }

    boolean columnExists(String table, String column) throws SQLException {
      return count(
              "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = " + db.schemaExpr
                  + " AND LOWER(TABLE_NAME) = ? AND LOWER(COLUMN_NAME) = ?",
              table,
              column)
          > 0;
    }

    private long count(String sql, String... params) throws SQLException {
      try (PreparedStatement ps = c.prepareStatement(sql)) {
        for (int i = 0; i < params.length; i++) {
          ps.setString(i + 1, params[i]);
        }
        try (ResultSet rs = ps.executeQuery()) {
          return rs.next() ? rs.getLong(1) : 0;
        }
      }
    }

    private List<String> names(String sql, String param) throws SQLException {
      List<String> out = new ArrayList<>();
      try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, param);
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            out.add(rs.getString(1));
          }
        }
      }
      return out;
    }

    private void execute(String sql) throws SQLException {
      try (Statement s = c.createStatement()) {
        s.execute(sql);
      }
    }

    private int update(String sql) throws SQLException {
      try (Statement s = c.createStatement()) {
        return s.executeUpdate(sql);
      }
    }
  }
}
