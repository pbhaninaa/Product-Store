package com.productstore.platform.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

import com.productstore.platform.entities.OrderEntity;
import com.productstore.platform.entities.SalonBookingEntity;
import com.productstore.platform.entities.ShopSettingsEntity;
import com.productstore.platform.entities.TenantEntity;
import com.productstore.platform.repositories.ShopSettingsRepository;
import com.productstore.platform.repositories.TenantRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MerchantNotificationService {
  private static final Logger log = LoggerFactory.getLogger(MerchantNotificationService.class);
  private static final DateTimeFormatter BOOKING_TIME_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Africa/Johannesburg"));

  private final TenantRepository tenants;
  private final ShopSettingsRepository shopSettings;
  private final InAppNotificationService inAppNotifications;
  private final MerchantSubscriptionService subscriptions;
  private final String fromEmail;
  private final String sendgridApiKey;
  private final String sendgridApiUrl;
  private final boolean whatsappEnabled;
  private final String twilioAccountSid;
  private final String twilioAuthToken;
  private final String twilioWhatsappFrom;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

  public MerchantNotificationService(
      TenantRepository tenants,
      ShopSettingsRepository shopSettings,
      InAppNotificationService inAppNotifications,
      MerchantSubscriptionService subscriptions,
      @Value("${app.email.from:no-reply@localhost}") String fromEmail,
      @Value("${sendgrid.apiKey:}") String sendgridApiKey,
      @Value("${app.twilio.sendgrid.api-url:https://api.sendgrid.com/v3/mail/send}") String sendgridApiUrl,
      @Value("${app.whatsapp.enabled:false}") boolean whatsappEnabled,
      @Value("${app.twilio.whatsapp.account-sid:}") String twilioAccountSid,
      @Value("${app.twilio.whatsapp.auth-token:}") String twilioAuthToken,
      @Value("${app.twilio.whatsapp.from:}") String twilioWhatsappFrom) {
    this.tenants = tenants;
    this.shopSettings = shopSettings;
    this.inAppNotifications = inAppNotifications;
    this.subscriptions = subscriptions;
    this.fromEmail = fromEmail;
    this.sendgridApiKey = sendgridApiKey == null ? "" : sendgridApiKey.trim();
    this.sendgridApiUrl = sendgridApiUrl == null ? "" : sendgridApiUrl.trim();
    this.whatsappEnabled = whatsappEnabled;
    this.twilioAccountSid = twilioAccountSid == null ? "" : twilioAccountSid.trim();
    this.twilioAuthToken = twilioAuthToken == null ? "" : twilioAuthToken.trim();
    this.twilioWhatsappFrom = twilioWhatsappFrom == null ? "" : twilioWhatsappFrom.trim();
  }

  public void notifyOrderPlaced(UUID tenantId, OrderEntity order) {
    if (tenantId == null || order == null) return;
    TenantEntity tenant = tenants.findById(tenantId).orElse(null);
    if (tenant == null) return;
    ShopSettingsEntity s = shopSettings.findByTenantId(tenantId).orElse(null);
    String subject = "New order placed - " + order.id;
    String msg =
        "New order placed for " + tenant.name + ".\n"
            + "Order: "
            + order.id
            + "\n"
            + "Customer: "
            + safe(order.customerName)
            + "\n"
            + "Total: R"
            + (order.totalZar == null ? "0.00" : order.totalZar.toPlainString())
            + "\n"
            + "Placed at: "
            + Instant.now();

    sendNotification(safe(s == null ? "" : s.contactEmail), safe(s == null ? "" : s.contactPhone), subject, msg, tenant);
    sendNotification(safe(order.customerEmail), safe(order.customerPhone), "Order received - " + order.id, msg, tenant);
    inAppNotifications.notifyTenantStaff(
        tenantId, subject, msg, "ORDER_PLACED", "ORDER", order.id.toString());
  }

  public void notifyOrderNeedsManualEftReview(UUID tenantId, OrderEntity order) {
    if (tenantId == null || order == null) return;
    TenantEntity tenant = tenants.findById(tenantId).orElse(null);
    if (tenant == null) return;
    ShopSettingsEntity s = shopSettings.findByTenantId(tenantId).orElse(null);
    String subject = "EFT requires manual review - " + order.id;
    String msg =
        "An EFT proof needs manual verification.\n"
            + "Order: "
            + order.id
            + "\n"
            + "Customer: "
            + safe(order.customerName)
            + "\n"
            + "Reference: "
            + safe(order.paymentReferenceDeclared);
    sendNotification(safe(s == null ? "" : s.contactEmail), safe(s == null ? "" : s.contactPhone), subject, msg, tenant);
    inAppNotifications.notifyTenantStaff(
        tenantId, subject, msg, "ORDER_EFT_REVIEW", "ORDER", order.id.toString());
  }

  public void notifyBookingPlaced(UUID tenantId, SalonBookingEntity booking) {
    if (tenantId == null || booking == null) return;
    TenantEntity tenant = tenants.findById(tenantId).orElse(null);
    if (tenant == null) return;
    ShopSettingsEntity s = shopSettings.findByTenantId(tenantId).orElse(null);
    String when = booking.startAt == null ? "" : BOOKING_TIME_FMT.format(booking.startAt);
    String subject = "New booking placed - " + booking.id;
    String msg =
        "New booking placed for " + tenant.name + ".\n"
            + "Booking: "
            + booking.id
            + "\n"
            + "Customer: "
            + safe(booking.customerName)
            + "\n"
            + "Time: "
            + when;
    sendNotification(safe(s == null ? "" : s.contactEmail), safe(s == null ? "" : s.contactPhone), subject, msg, tenant);
    sendNotification(
        safe(booking.customerEmail), safe(booking.customerPhone), "Booking received - " + booking.id, msg, tenant);
    inAppNotifications.notifyTenantStaff(
        tenantId, subject, msg, "BOOKING_PLACED", "SALON_BOOKING", booking.id.toString());
  }

  public void notifyBookingNeedsManualEftReview(UUID tenantId, SalonBookingEntity booking) {
    if (tenantId == null || booking == null) return;
    TenantEntity tenant = tenants.findById(tenantId).orElse(null);
    if (tenant == null) return;
    ShopSettingsEntity s = shopSettings.findByTenantId(tenantId).orElse(null);
    String subject = "Booking EFT requires manual review - " + booking.id;
    String msg =
        "A booking EFT proof needs manual verification.\n"
            + "Booking: "
            + booking.id
            + "\n"
            + "Customer: "
            + safe(booking.customerName)
            + "\n"
            + "Reference: "
            + safe(booking.paymentReferenceDeclared);
    sendNotification(safe(s == null ? "" : s.contactEmail), safe(s == null ? "" : s.contactPhone), subject, msg, tenant);
    inAppNotifications.notifyTenantStaff(
        tenantId, subject, msg, "BOOKING_EFT_REVIEW", "SALON_BOOKING", booking.id.toString());
  }

  private void sendNotification(String toEmail, String toPhone, String subject, String message, TenantEntity tenant) {
    if (isEmailAlertsAllowed(tenant)) {
      sendEmail(toEmail, subject, message);
    }
    if (isWhatsappAllowed(tenant)) {
      sendWhatsapp(toPhone, message);
    }
  }

  private void sendEmail(String toEmail, String subject, String message) {
    String to = safe(toEmail);
    if (to.isBlank() || sendgridApiKey.isBlank() || sendgridApiUrl.isBlank()) return;
    String body =
        "{"
            + "\"from\":{\"email\":\""
            + json(fromEmail)
            + "\"},"
            + "\"personalizations\":[{\"to\":[{\"email\":\""
            + json(to)
            + "\"}]}],"
            + "\"subject\":\""
            + json(subject)
            + "\","
            + "\"content\":[{\"type\":\"text/plain\",\"value\":\""
            + json(message)
            + "\"}]"
            + "}";
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(sendgridApiUrl))
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Bearer " + sendgridApiKey)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        log.warn("sendgrid email failed for {} status={}", to, res.statusCode());
      }
    } catch (Exception e) {
      log.warn("email notification failed for {}: {}", to, e.getMessage());
    }
  }

  private void sendWhatsapp(String rawPhone, String message) {
    String to = normalizePhone(rawPhone);
    if (to.isBlank() || !whatsappEnabled || twilioAccountSid.isBlank() || twilioAuthToken.isBlank()) return;
    String twilioTo = "whatsapp:" + to;
    String twilioFrom = normalizeTwilioFrom(twilioWhatsappFrom);
    if (twilioFrom.isBlank()) return;
    String formBody =
        "To="
            + url(twilioTo)
            + "&From="
            + url(twilioFrom)
            + "&Body="
            + url(message);
    try {
      String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";
      String basic =
          Base64.getEncoder()
              .encodeToString((twilioAccountSid + ":" + twilioAuthToken).getBytes(StandardCharsets.UTF_8));
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Basic " + basic)
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        log.warn("whatsapp notification failed for {} status={}", to, res.statusCode());
      }
    } catch (Exception e) {
      log.warn("whatsapp notification failed for {}: {}", to, e.getMessage());
    }
  }

  private boolean isEmailAlertsAllowed(TenantEntity tenant) {
    if (tenant == null || tenant.id == null) return false;
    try {
      return subscriptions.grantsFeature(tenant.id, "emailAlerts");
    } catch (Exception e) {
      return false;
    }
  }

  private boolean isWhatsappAllowed(TenantEntity tenant) {
    if (tenant == null || tenant.id == null) return false;
    try {
      return subscriptions.grantsFeature(tenant.id, "whatsapp");
    } catch (Exception e) {
      return false;
    }
  }

  private static String normalizeTwilioFrom(String from) {
    String n = normalizePhone(from);
    if (n.isBlank()) return "";
    return "whatsapp:" + n;
  }

  private static String normalizePhone(String rawPhone) {
    String p = safe(rawPhone).replaceAll("\\s+", "");
    if (p.startsWith("00")) p = "+" + p.substring(2);
    if (p.startsWith("0")) p = "+27" + p.substring(1);
    if (!p.startsWith("+")) p = "+" + p.replaceAll("[^0-9]", "");
    return p.matches("^\\+[0-9]{8,20}$") ? p : "";
  }

  private static String safe(String s) {
    return s == null ? "" : s.trim();
  }

  private static String json(String s) {
    return (s == null ? "" : s)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "");
  }

  private static String url(String s) {
    String t = s == null ? "" : s;
    return java.net.URLEncoder.encode(t, StandardCharsets.UTF_8);
  }
}
