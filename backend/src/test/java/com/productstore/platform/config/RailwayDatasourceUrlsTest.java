package com.productstore.platform.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RailwayDatasourceUrlsTest {

  @Test
  void prefixesMysqlSchemeAndDisablesSslWhenMissing() {
    String out = RailwayDatasourceUrls.normalize("mysql://root:pass@mysql.railway.internal:3306/railway");
    assertTrue(out.startsWith("jdbc:mysql://root:pass@mysql.railway.internal:3306/railway"));
    assertTrue(out.contains("useSSL=false"));
  }

  @Test
  void leavesH2Alone() {
    assertEquals("jdbc:h2:mem:testdb", RailwayDatasourceUrls.normalize("jdbc:h2:mem:testdb"));
  }

  @Test
  void flipsSslOnRailwayPrivateHosts() {
    String out =
        RailwayDatasourceUrls.normalize(
            "jdbc:mysql://mysql.railway.internal:3306/railway?useSSL=true&serverTimezone=UTC");
    assertTrue(out.contains("useSSL=false"));
  }

  @Test
  void prefersMysqlUrlOverLocalhostProfileUrl() {
    String chosen =
        RailwayDatasourceUrls.pickUrl(
            null,
            "jdbc:mysql://localhost:3306/Product-site?useSSL=false",
            "mysql://root:secret@mysql.railway.internal:3306/railway");
    assertEquals("mysql://root:secret@mysql.railway.internal:3306/railway", chosen);
  }

  @Test
  void keepsExplicitNonLocalSpringUrl() {
    String chosen =
        RailwayDatasourceUrls.pickUrl(
            "jdbc:mysql://mysql.railway.internal:3306/railway",
            "jdbc:mysql://localhost:3306/Product-site",
            "mysql://root:secret@mysql.railway.internal:3306/railway");
    assertEquals("jdbc:mysql://mysql.railway.internal:3306/railway", chosen);
  }

  @Test
  void extractsUserAndPasswordFromMysqlUrl() {
    String url = "mysql://root:p%40ss@mysql.railway.internal:3306/railway";
    assertEquals("mysql.railway.internal", RailwayDatasourceUrls.extractHost(url));
    assertEquals("root", RailwayDatasourceUrls.extractUser(url));
    assertEquals("p%40ss", RailwayDatasourceUrls.extractPassword(url));
  }

  @Test
  void doesNotReplaceH2WithMysqlUrl() {
    String chosen =
        RailwayDatasourceUrls.pickUrl(
            null,
            "jdbc:h2:mem:productstore_sit;MODE=MySQL",
            "mysql://root:secret@mysql.railway.internal:3306/railway");
    assertEquals("jdbc:h2:mem:productstore_sit;MODE=MySQL", chosen);
  }
}
