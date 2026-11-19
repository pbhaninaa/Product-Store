package com.productstore.platform.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/**
 * Store images (product photos, store logo/banner, salon service photos) are saved in the database
 * and served publicly so every visitor — guest, client, merchant or support — can see them.
 *
 * <p>Uploads are checked by their actual bytes, not the name or the type the browser claims: only
 * JPEG, PNG, GIF and WebP (and SVG where explicitly allowed, e.g. store logos) up to 5 MB are kept,
 * and the stored content type is the detected one.
 */
public final class ImageUploads {
  public static final long MAX_BYTES = 5L * 1024 * 1024;

  private ImageUploads() {}

  /** A validated image ready to save. */
  public record Image(byte[] data, String contentType) {}

  /** Raster images only (JPEG, PNG, GIF, WebP). */
  public static Image read(MultipartFile file) throws IOException {
    return read(file, false);
  }

  /**
   * @throws IllegalArgumentException {@code image_too_large} or {@code image_type_not_supported}
   */
  public static Image read(MultipartFile file, boolean allowSvg) throws IOException {
    if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("image_too_large");
    byte[] data = file.getBytes();
    if (data.length > MAX_BYTES) throw new IllegalArgumentException("image_too_large");
    String type = detect(data, allowSvg);
    if (type == null) throw new IllegalArgumentException("image_type_not_supported");
    return new Image(data, type);
  }

  /** JPEG/PNG/GIF/WebP content type from the bytes (never SVG), or null. */
  public static String detectRaster(byte[] data) {
    return detect(data, false);
  }

  /** Content type from the file's magic bytes, or null when it is not a supported image. */
  static String detect(byte[] d, boolean allowSvg) {
    if (d == null || d.length < 12) return null;
    if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
    if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
    if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') return "image/gif";
    if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
        && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
      return "image/webp";
    }
    if (allowSvg) {
      String head =
          new String(d, 0, Math.min(d.length, 1024), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
      if (head.startsWith("<svg") || (head.startsWith("<?xml") && head.contains("<svg"))) return "image/svg+xml";
    }
    return null;
  }

  /**
   * Public image response. Browsers keep a copy and re-check it on each view (ETag → 304 when
   * unchanged), so a replaced image shows straight away without re-downloading unchanged ones.
   * The sandbox CSP stops an SVG from running scripts if someone opens it directly.
   */
  public static ResponseEntity<byte[]> respond(byte[] data, String contentType, String ifNoneMatch) {
    if (data == null || data.length == 0) return ResponseEntity.notFound().build();
    String etag = "\"" + sha256(data).substring(0, 32) + "\"";
    CacheControl cache = CacheControl.maxAge(0, TimeUnit.SECONDS).cachePublic().mustRevalidate();
    if (ifNoneMatch != null && ifNoneMatch.contains(etag)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
    }
    String ct = contentType == null || contentType.isBlank() ? "image/jpeg" : contentType;
    MediaType mt;
    try {
      mt = MediaType.parseMediaType(ct);
    } catch (RuntimeException e) {
      mt = MediaType.IMAGE_JPEG;
    }
    return ResponseEntity.ok()
        .contentType(mt)
        .eTag(etag)
        .cacheControl(cache)
        .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .body(data);
  }

  private static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
