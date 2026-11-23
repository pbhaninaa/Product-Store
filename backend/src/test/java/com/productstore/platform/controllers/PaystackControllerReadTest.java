package com.productstore.platform.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

/** The bounded body read behind the webhook (also covers bodies sent without a Content-Length). */
class PaystackControllerReadTest {
  @Test
  void readsBodiesUpToTheLimit() throws Exception {
    byte[] body = new byte[PaystackController.MAX_WEBHOOK_BYTES];
    assertThat(PaystackController.readAtMost(new ByteArrayInputStream(body), PaystackController.MAX_WEBHOOK_BYTES))
        .hasSize(PaystackController.MAX_WEBHOOK_BYTES);
  }

  @Test
  void stopsAsSoonAsTheLimitIsPassed() throws Exception {
    byte[] body = new byte[PaystackController.MAX_WEBHOOK_BYTES + 1];
    assertThat(PaystackController.readAtMost(new ByteArrayInputStream(body), PaystackController.MAX_WEBHOOK_BYTES))
        .isNull();
  }
}
