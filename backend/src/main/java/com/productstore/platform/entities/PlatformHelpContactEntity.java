package com.productstore.platform.entities;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_help_contact")
public class PlatformHelpContactEntity {
  @Id
  public UUID id;

  @Column(name = "support_email", length = 320)
  public String supportEmail = "";

  @Column(name = "support_phone", length = 40)
  public String supportPhone = "";

  @Column(name = "whatsapp", length = 40)
  public String whatsapp = "";

  @Column(name = "hours_text", length = 500)
  public String hoursText = "";

  @Column(length = 1000)
  public String notes = "";

  @PrePersist
  public void prePersist() {
    if (id == null) id = UUID.randomUUID();
    if (supportEmail == null) supportEmail = "";
    if (supportPhone == null) supportPhone = "";
    if (whatsapp == null) whatsapp = "";
    if (hoursText == null) hoursText = "";
    if (notes == null) notes = "";
  }
}
