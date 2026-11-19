package com.productstore.platform.services;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Seed-only pictures for the SIT demo store (logo, banner, product and service photos). Drawn at
 * startup so the repo needs no binary assets and nothing is downloaded. Returns null if drawing
 * fails (e.g. a server image with no fonts), in which case the seed simply skips the picture.
 */
final class SeedImages {
  private SeedImages() {}

  /** Square card for a product or service: soft gradient, decorative circles, big initial, name. */
  static byte[] card(String title, String caption, Color from, Color to) {
    return draw(800, 800, from, to, (g, w, h) -> {
      g.setColor(new Color(255, 255, 255, 40));
      g.fill(new Ellipse2D.Double(w * 0.55, -h * 0.15, w * 0.7, w * 0.7));
      g.fill(new Ellipse2D.Double(-w * 0.2, h * 0.6, w * 0.6, w * 0.6));
      g.setColor(new Color(255, 255, 255, 230));
      g.fill(new Ellipse2D.Double(w / 2.0 - 130, h * 0.22, 260, 260));
      g.setColor(to.darker());
      centered(g, initials(title), new Font(Font.SANS_SERIF, Font.BOLD, 120), w, (int) (h * 0.22 + 175));
      g.setColor(Color.WHITE);
      wrapCentered(g, title, new Font(Font.SANS_SERIF, Font.BOLD, 54), w, (int) (h * 0.68), 2);
      g.setColor(new Color(255, 255, 255, 210));
      centered(g, caption, new Font(Font.SANS_SERIF, Font.PLAIN, 32), w, (int) (h * 0.9));
    });
  }

  /** Round-cornered store logo with the store's initials. */
  static byte[] logo(String storeName, Color from, Color to) {
    return draw(512, 512, from, to, (g, w, h) -> {
      g.setColor(new Color(255, 255, 255, 235));
      g.setStroke(new BasicStroke(14f));
      g.draw(new RoundRectangle2D.Double(46, 46, w - 92, h - 92, 90, 90));
      centered(g, initials(storeName), new Font(Font.SANS_SERIF, Font.BOLD, 190), w, h / 2 + 68);
    });
  }

  /** Wide storefront banner with the store name and a tagline. */
  static byte[] banner(String storeName, String tagline, Color from, Color to) {
    return draw(1600, 600, from, to, (g, w, h) -> {
      g.setColor(new Color(255, 255, 255, 36));
      for (int i = 0; i < 6; i++) {
        double d = 180 + i * 70;
        g.fill(new Ellipse2D.Double(w - d * 1.4 + i * 30, -d / 3 + i * 40, d, d));
      }
      g.setColor(Color.WHITE);
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 110));
      g.drawString(storeName, 110, h / 2 + 10);
      g.setColor(new Color(255, 255, 255, 215));
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 46));
      g.drawString(tagline, 114, h / 2 + 90);
    });
  }

  private interface Painter {
    void paint(Graphics2D g, int w, int h);
  }

  private static byte[] draw(int w, int h, Color from, Color to, Painter painter) {
    try {
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setPaint(new GradientPaint(0, 0, from, w, h, to));
      g.fillRect(0, 0, w, h);
      painter.paint(g, w, h);
      g.dispose();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(img, "png", out);
      return out.toByteArray();
    } catch (Throwable e) {
      return null;
    }
  }

  private static String initials(String s) {
    StringBuilder b = new StringBuilder();
    for (String part : String.valueOf(s).trim().split("\\s+")) {
      if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0))) b.append(Character.toUpperCase(part.charAt(0)));
      if (b.length() == 2) break;
    }
    return b.length() == 0 ? "?" : b.toString();
  }

  private static void centered(Graphics2D g, String text, Font font, int w, int baseline) {
    g.setFont(font);
    FontMetrics fm = g.getFontMetrics();
    g.drawString(text, (w - fm.stringWidth(text)) / 2, baseline);
  }

  private static void wrapCentered(Graphics2D g, String text, Font font, int w, int baseline, int maxLines) {
    g.setFont(font);
    FontMetrics fm = g.getFontMetrics();
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : text.split("\\s+")) {
      String next = line.length() == 0 ? word : line + " " + word;
      if (fm.stringWidth(next) > w - 120 && line.length() > 0) {
        lines.add(line.toString());
        line = new StringBuilder(word);
      } else {
        line = new StringBuilder(next);
      }
    }
    if (line.length() > 0) lines.add(line.toString());
    int start = baseline - (Math.min(lines.size(), maxLines) - 1) * fm.getHeight() / 2;
    for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
      centered(g, lines.get(i), font, w, start + i * fm.getHeight());
    }
  }
}
