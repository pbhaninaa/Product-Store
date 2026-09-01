package com.productstore.platform.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * PayFast MD5 signature: concatenate {@code key=urlencode(value)} for non-blank fields
 * (excluding {@code signature}) in iteration order, then append {@code &passphrase=} when set.
 * Encoding matches PHP {@code urlencode}: spaces as {@code +}, hex uppercase.
 */
public final class PayFastSignatureUtil {

  private PayFastSignatureUtil() {}

  public static String buildSignature(Map<String, String> fields, String passphrase) {
    StringBuilder sb = new StringBuilder();
    if (fields != null) {
      for (Map.Entry<String, String> entry : fields.entrySet()) {
        String key = entry.getKey();
        if (key == null || "signature".equalsIgnoreCase(key)) {
          continue;
        }
        String value = entry.getValue();
        if (value == null || value.isBlank()) {
          continue;
        }
        if (sb.length() > 0) {
          sb.append('&');
        }
        sb.append(key).append('=').append(urlEncode(value.trim()));
      }
    }
    if (passphrase != null && !passphrase.isBlank()) {
      sb.append("&passphrase=").append(urlEncode(passphrase.trim()));
    }
    return md5Hex(sb.toString());
  }

  public static boolean signaturesMatch(String expected, String actual) {
    if (expected == null || actual == null) {
      return false;
    }
    byte[] expectedBytes = expected.toLowerCase().getBytes(StandardCharsets.US_ASCII);
    byte[] actualBytes = actual.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
    return MessageDigest.isEqual(expectedBytes, actualBytes);
  }

  static String urlEncode(String value) {
    if (value == null) {
      return "";
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    StringBuilder out = new StringBuilder(bytes.length * 3);
    for (byte b : bytes) {
      int c = b & 0xFF;
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_'
          || c == '.') {
        out.append((char) c);
      } else if (c == ' ') {
        out.append('+');
      } else {
        out.append('%');
        out.append(String.format("%02X", c));
      }
    }
    return out.toString();
  }

  private static String md5Hex(String data) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 not available", e);
    }
  }
}
