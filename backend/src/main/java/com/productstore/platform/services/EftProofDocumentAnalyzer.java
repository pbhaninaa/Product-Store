package com.productstore.platform.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Best-effort auto-check of bank EFT PDFs: extract text and require an amount matching the expected total and a
 * transaction-style date on the slip that matches today (or the previous calendar day in the store zone) to reduce
 * false negatives around cut-off. If parsing fails or values do not match, the caller should route to manual review.
 */
@Component
public class EftProofDocumentAnalyzer {
  public static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Johannesburg");

  public static final Set<String> PROOF_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

  private static final int MIN_TEXT_CHARS = 24;
  private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
  private static final Pattern SLASH_DATE = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{2,4})\\b");
  private static final Pattern DASH_DMY = Pattern.compile("\\b(\\d{1,2})-(\\d{1,2})-(\\d{4})\\b");

  private static final Pattern MONEY_BLOCKS =
      Pattern.compile(
          "(?:R\\s*|ZAR\\s*)?(\\d{1,3}(?:[\\s,]\\d{3})+(?:[.,]\\d{1,2})?|\\d{1,3}(?:[.,]\\d{1,2})?)",
          Pattern.CASE_INSENSITIVE);

  public boolean isPdfFilename(String filename) {
    String ext = extension(filename);
    return "pdf".equals(ext);
  }

  /**
   * Resolves allowed proof extension from filename and/or PDF magic bytes. Throws {@link IllegalArgumentException} if
   * unsupported.
   */
  public String resolveProofUploadExtension(String filename, byte[] payload) {
    if (payload == null || payload.length == 0) {
      throw new IllegalArgumentException("proof_required");
    }
    String fromName = extension(filename);
    if ("pdf".equals(fromName)) {
      byte[] head = payload.length >= 5 ? Arrays.copyOf(payload, 5) : payload;
      if (!isPdfMagic(head)) {
        throw new IllegalArgumentException("invalid_pdf");
      }
      return "pdf";
    }
    if (PROOF_IMAGE_EXTENSIONS.contains(fromName)) {
      return fromName;
    }
    byte[] head = payload.length >= 5 ? Arrays.copyOf(payload, 5) : payload;
    if (isPdfMagic(head)) {
      return "pdf";
    }
    throw new IllegalArgumentException("unsupported_proof_type");
  }

  public boolean isPdfMagic(byte[] header) {
    if (header == null || header.length < 5) return false;
    return header[0] == '%'
        && header[1] == 'P'
        && header[2] == 'D'
        && header[3] == 'F'
        && header[4] == '-';
  }

  /**
   * Reads PDF text and checks expected ZAR amount (scale 2) appears and a slip date equals today or yesterday in
   * {@code zone}.
   */
  public boolean verifyPdfAmountAndRecentDate(byte[] pdfBytes, BigDecimal expectedZar, ZoneId zone) {
    if (pdfBytes == null || pdfBytes.length < 32 || !isPdfMagic(pdfBytes)) {
      return false;
    }
    if (expectedZar == null || expectedZar.compareTo(BigDecimal.ZERO) <= 0) {
      return false;
    }
    BigDecimal want = expectedZar.setScale(2, RoundingMode.HALF_UP);
    String text;
    try {
      text = extractPdfText(pdfBytes);
    } catch (IOException e) {
      return false;
    }
    if (text == null || text.trim().length() < MIN_TEXT_CHARS) {
      return false;
    }
    String norm = text.replace('\u00a0', ' ');
    boolean amountOk = containsMatchingAmount(norm, want);
    boolean dateOk = containsAllowedSlipDate(norm, zone);
    return amountOk && dateOk;
  }

  public String extractPdfText(byte[] pdfBytes) throws IOException {
    try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
      if (doc.isEncrypted()) {
        throw new IOException("encrypted_pdf");
      }
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      return stripper.getText(doc);
    }
  }

  private static boolean containsMatchingAmount(String text, BigDecimal want) {
    Matcher m = MONEY_BLOCKS.matcher(text);
    while (m.find()) {
      Optional<BigDecimal> parsed = parseMoneyToken(m.group(1));
      if (parsed.isEmpty()) continue;
      BigDecimal p = parsed.get().setScale(2, RoundingMode.HALF_UP);
      if (p.compareTo(want) == 0) {
        return true;
      }
    }
    return false;
  }

  /** Parses tokens like "1,234.56", "1234.56", "1 234,56" (comma decimals). */
  static Optional<BigDecimal> parseMoneyToken(String raw) {
    if (raw == null) return Optional.empty();
    String s = raw.trim().replace(" ", "");
    if (s.isEmpty()) return Optional.empty();
    try {
      if (s.contains(",") && s.contains(".")) {
        int lastComma = s.lastIndexOf(',');
        int lastDot = s.lastIndexOf('.');
        if (lastComma > lastDot) {
          s = s.replace(".", "").replace(',', '.');
        } else {
          s = s.replace(",", "");
        }
      } else if (s.contains(",") && !s.contains(".")) {
        int last = s.lastIndexOf(',');
        String frac = s.substring(last + 1);
        if (frac.length() == 2 && frac.chars().allMatch(Character::isDigit)) {
          s = s.substring(0, last).replace(",", "") + "." + frac;
        } else {
          s = s.replace(",", "");
        }
      } else {
        s = s.replace(",", "");
      }
      return Optional.of(new BigDecimal(s));
    } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
      return Optional.empty();
    }
  }

  private static boolean containsAllowedSlipDate(String text, ZoneId zone) {
    LocalDate today = LocalDate.now(zone);
    LocalDate yesterday = today.minusDays(1);
    Set<LocalDate> found = new HashSet<>();
    collectIsoDates(text, found);
    collectSlashDates(text, found);
    collectDashDmyDates(text, found);
    for (LocalDate d : found) {
      if (d.equals(today) || d.equals(yesterday)) {
        return true;
      }
    }
    return false;
  }

  private static void collectIsoDates(String text, Set<LocalDate> out) {
    Matcher m = ISO_DATE.matcher(text);
    while (m.find()) {
      try {
        out.add(LocalDate.parse(m.group(0), DateTimeFormatter.ISO_LOCAL_DATE));
      } catch (DateTimeParseException ignored) {
        // skip
      }
    }
  }

  private static void collectSlashDates(String text, Set<LocalDate> out) {
    Matcher m = SLASH_DATE.matcher(text);
    while (m.find()) {
      int a = Integer.parseInt(m.group(1));
      int b = Integer.parseInt(m.group(2));
      int yRaw = Integer.parseInt(m.group(3));
      int y = yRaw < 100 ? 2000 + yRaw : yRaw;
      if (b >= 1 && b <= 12 && a >= 1 && a <= 31) {
        try {
          out.add(LocalDate.of(y, b, a));
        } catch (Exception ignored) {
          // skip invalid d/M/y
        }
      }
      if (a >= 1 && a <= 12 && b >= 1 && b <= 31) {
        try {
          out.add(LocalDate.of(y, a, b));
        } catch (Exception ignored) {
          // skip invalid M/d/y
        }
      }
    }
  }

  private static void collectDashDmyDates(String text, Set<LocalDate> out) {
    Matcher m = DASH_DMY.matcher(text);
    while (m.find()) {
      try {
        int d = Integer.parseInt(m.group(1));
        int mo = Integer.parseInt(m.group(2));
        int y = Integer.parseInt(m.group(3));
        out.add(LocalDate.of(y, mo, d));
      } catch (Exception ignored) {
        // skip
      }
    }
  }

  private static String extension(String filename) {
    if (filename == null) return "";
    int i = filename.lastIndexOf('.');
    if (i < 0 || i >= filename.length() - 1) return "";
    return filename.substring(i + 1).toLowerCase(Locale.ROOT);
  }
}
