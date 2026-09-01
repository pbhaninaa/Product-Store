package com.productstore.platform.util;

import java.util.regex.Pattern;

/** Customer/account emails. Allows a normal domain or {@code @localhost} for local/SIT seeds. */
public final class Emails {
  private static final Pattern VALID =
      Pattern.compile("^[^@\\s]+@([^@\\s]+\\.[^@\\s]+|localhost)$", Pattern.CASE_INSENSITIVE);

  private Emails() {}

  public static boolean isValid(String email) {
    if (email == null) return false;
    String v = email.trim();
    return !v.isEmpty() && VALID.matcher(v).matches();
  }
}
