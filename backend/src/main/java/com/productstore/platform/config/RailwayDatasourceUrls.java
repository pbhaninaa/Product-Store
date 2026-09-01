package com.productstore.platform.config;

/**
 * Railway MySQL plugin exposes {@code MYSQL_URL=mysql://...}. Spring needs {@code jdbc:mysql://}.
 * Private-network hosts also fail when the driver defaults to SSL.
 */
public final class RailwayDatasourceUrls {
  private RailwayDatasourceUrls() {}

  public static String normalize(String raw) {
    if (raw == null) return null;
    String url = raw.trim();
    if (url.isEmpty()) return url;
    if (url.regionMatches(true, 0, "mysql://", 0, 8)) {
      url = "jdbc:" + url;
    }
    if (!url.regionMatches(true, 0, "jdbc:mysql://", 0, 13)) {
      return url;
    }
    return ensureMysqlParams(url);
  }

  public static String pickUrl(String springDatasourceUrl, String resolvedDatasourceUrl, String mysqlUrl) {
    String spring = blankToNull(springDatasourceUrl);
    String resolved = blankToNull(resolvedDatasourceUrl);
    String mysql = blankToNull(mysqlUrl);
    if (mysql != null && shouldPreferRailwayMysql(firstNonBlank(spring, resolved), mysql)) {
      return mysql;
    }
    String chosen = firstNonBlank(spring, resolved, mysql);
    return chosen;
  }

  static boolean shouldPreferRailwayMysql(String currentUrl, String mysqlUrl) {
    if (blankToNull(mysqlUrl) == null) {
      return false;
    }
    if (currentUrl != null) {
      String lower = currentUrl.trim().toLowerCase();
      if (lower.startsWith("jdbc:h2:") || lower.startsWith("jdbc:postgresql:")) {
        return false;
      }
    }
    String host = extractHost(currentUrl);
    return host == null || host.isBlank() || isLoopbackHost(host);
  }

  public static String extractHost(String raw) {
    String authority = authority(raw);
    if (authority == null) {
      return null;
    }
    int at = authority.lastIndexOf('@');
    String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
    if (hostPort.startsWith("[")) {
      int close = hostPort.indexOf(']');
      return close > 0 ? hostPort.substring(1, close) : hostPort;
    }
    int colon = hostPort.lastIndexOf(':');
    if (colon > 0 && hostPort.indexOf(':') == colon) {
      return hostPort.substring(0, colon);
    }
    return hostPort;
  }

  public static String extractUser(String raw) {
    String userInfo = userInfo(raw);
    if (userInfo == null) {
      return null;
    }
    int colon = userInfo.indexOf(':');
    return colon < 0 ? userInfo : userInfo.substring(0, colon);
  }

  public static String extractPassword(String raw) {
    String userInfo = userInfo(raw);
    if (userInfo == null) {
      return null;
    }
    int colon = userInfo.indexOf(':');
    return colon < 0 ? null : userInfo.substring(colon + 1);
  }

  static boolean isLoopbackHost(String host) {
    if (host == null) {
      return true;
    }
    String h = host.trim().toLowerCase();
    return h.isEmpty() || "localhost".equals(h) || "127.0.0.1".equals(h) || "::1".equals(h);
  }

  static String ensureMysqlParams(String jdbcUrl) {
    String lower = jdbcUrl.toLowerCase();
    boolean railwayPrivate = lower.contains(".railway.internal");
    if (railwayPrivate) {
      jdbcUrl = jdbcUrl.replace("useSSL=true", "useSSL=false").replace("useSSL=TRUE", "useSSL=false");
      jdbcUrl = jdbcUrl.replace("sslMode=REQUIRED", "sslMode=DISABLED").replace("sslMode=required", "sslMode=DISABLED");
    }
    boolean hasSsl =
        lower.contains("usessl=") || lower.contains("sslmode=") || lower.contains("usessl =");
    if (hasSsl) {
      return jdbcUrl;
    }
    String sep = jdbcUrl.contains("?") ? "&" : "?";
    return jdbcUrl
        + sep
        + "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
  }

  private static String userInfo(String raw) {
    String authority = authority(raw);
    if (authority == null) {
      return null;
    }
    int at = authority.lastIndexOf('@');
    if (at <= 0) {
      return null;
    }
    return authority.substring(0, at);
  }

  private static String authority(String raw) {
    if (raw == null) {
      return null;
    }
    String url = raw.trim();
    int scheme = url.indexOf("://");
    if (scheme < 0) {
      return null;
    }
    String rest = url.substring(scheme + 3);
    int end = rest.length();
    int slash = rest.indexOf('/');
    int query = rest.indexOf('?');
    if (slash >= 0) {
      end = Math.min(end, slash);
    }
    if (query >= 0) {
      end = Math.min(end, query);
    }
    if (end <= 0) {
      return "";
    }
    return rest.substring(0, end);
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (blankToNull(v) != null) {
        return v.trim();
      }
    }
    return null;
  }
}
