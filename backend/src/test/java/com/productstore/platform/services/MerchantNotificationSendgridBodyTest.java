package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class MerchantNotificationSendgridBodyTest {
  @Test
  void controlCharactersProduceValidJsonWithTheSameShape() throws Exception {
    String body =
        MerchantNotificationService.sendgridBody(
            "no-reply@shop.test", "owner@shop.test", "New \"order\"", "Name:\tJo\\e\r\nLine2\u0001");
    JsonNode n = new ObjectMapper().readTree(body);
    assertThat(n.at("/from/email").asText()).isEqualTo("no-reply@shop.test");
    assertThat(n.at("/personalizations/0/to/0/email").asText()).isEqualTo("owner@shop.test");
    assertThat(n.at("/subject").asText()).isEqualTo("New \"order\"");
    assertThat(n.at("/content/0/type").asText()).isEqualTo("text/plain");
    assertThat(n.at("/content/0/value").asText()).isEqualTo("Name:\tJo\\e\nLine2\u0001");
  }
}
