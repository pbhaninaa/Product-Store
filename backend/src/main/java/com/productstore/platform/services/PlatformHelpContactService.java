package com.productstore.platform.services;

import java.util.LinkedHashMap;
import java.util.Map;

import com.productstore.platform.entities.PlatformHelpContactEntity;
import com.productstore.platform.repositories.PlatformHelpContactRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformHelpContactService {
  private final PlatformHelpContactRepository contacts;

  public PlatformHelpContactService(PlatformHelpContactRepository contacts) {
    this.contacts = contacts;
  }

  public Map<String, Object> get() {
    return toMap(ensure());
  }

  @Transactional
  public Map<String, Object> update(Map<String, Object> body) {
    PlatformHelpContactEntity c = ensure();
    if (body.get("supportEmail") != null) c.supportEmail = String.valueOf(body.get("supportEmail")).trim();
    if (body.get("supportPhone") != null) c.supportPhone = String.valueOf(body.get("supportPhone")).trim();
    if (body.get("whatsapp") != null) c.whatsapp = String.valueOf(body.get("whatsapp")).trim();
    if (body.get("hoursText") != null) c.hoursText = String.valueOf(body.get("hoursText")).trim();
    if (body.get("notes") != null) c.notes = String.valueOf(body.get("notes")).trim();
    contacts.save(c);
    return toMap(c);
  }

  private PlatformHelpContactEntity ensure() {
    return contacts.findAll().stream()
        .findFirst()
        .orElseGet(
            () -> {
              PlatformHelpContactEntity c = new PlatformHelpContactEntity();
              c.supportEmail = "support@productstore.local";
              c.hoursText = "Mon-Fri 09:00-17:00 SAST";
              return contacts.save(c);
            });
  }

  private static Map<String, Object> toMap(PlatformHelpContactEntity c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("supportEmail", nz(c.supportEmail));
    m.put("supportPhone", nz(c.supportPhone));
    m.put("whatsapp", nz(c.whatsapp));
    m.put("hoursText", nz(c.hoursText));
    m.put("notes", nz(c.notes));
    return m;
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
