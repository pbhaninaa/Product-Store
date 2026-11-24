package com.productstore.platform.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.productstore.platform.util.ImageUploads;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Legacy Manual EFT proof checks, kept only for orders and bookings placed before Manual EFT was removed
 * (2026-11-23): validates the type of a late proof upload, and blocks a merchant approving a PDF proof whose amount
 * or reference clearly does not match. Never auto-confirms a payment.
 */
@Component
public class EftProofDocumentAnalyzer {
  public static final Set<String> PROOF_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

  private static final int MIN_TEXT_CHARS = 24;

  private static final Pattern MONEY_BLOCKS =
      Pattern.compile(
          "(?:R\\s*|ZAR\\s*)?(\\d{1,3}(?:[\\s,]\\d{3})+(?:[.,]\\d{1,2})?|\\d{1,3}(?:[.,]\\d{1,2})?)",
          Pattern.CASE_INSENSITIVE);

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
    byte[] head = payload.length >= 5 ? Arrays.copyOf(payload, 5) : payload;
    if (isPdfMagic(head)) {
      return "pdf";
    }
    // Images are trusted by their bytes, never by the filename: "proof.png" holding HTML or a script is refused.
    // The returned subtype (jpeg/png/gif/webp) becomes the stored content type.
    String detected = ImageUploads.detectRaster(payload);
    if (detected != null) {
      return detected.substring("image/".length());
    }
    if (PROOF_IMAGE_EXTENSIONS.contains(fromName)) {
      throw new IllegalArgumentException("invalid_image");
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
   * Amount + payment reference only (no date). Used when a human is approving a manual-pending proof —
   * blocks approval when the PDF clearly does not match amount/ref.
   */
  public boolean verifyPdfAmountAndReference(
      byte[] pdfBytes, BigDecimal expectedZar, String paymentReference) {
    if (paymentReference == null || paymentReference.isBlank()) {
      return false;
    }
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
    if (!containsMatchingAmount(norm, want)) {
      return false;
    }
    String needle = paymentReference.trim().toUpperCase(Locale.ROOT);
    return text.toUpperCase(Locale.ROOT).contains(needle);
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

  private static String extension(String filename) {
    if (filename == null) return "";
    int i = filename.lastIndexOf('.');
    if (i < 0 || i >= filename.length() - 1) return "";
    return filename.substring(i + 1).toLowerCase(Locale.ROOT);
  }
}
