package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.productstore.platform.config.PaystackProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaystackVerifierTest {
  private PaystackClient client;
  private PaystackProperties properties;
  private PaystackVerifier verifier;

  @BeforeEach
  void setUp() {
    client = mock(PaystackClient.class);
    properties = new PaystackProperties();
    ReflectionTestUtils.setField(properties, "verifyCacheTtl", Duration.ofSeconds(10));
    verifier = new PaystackVerifier(client, properties);
  }

  @Test
  void repeatedVerifiesWithinTheTtlAskPaystackOnce() {
    PaystackClient.Transaction t =
        new PaystackClient.Transaction("1", "success", "ORDX", 100, "ZAR", "card", "test", Map.of());
    when(client.verify("ORDX")).thenReturn(Optional.of(t));

    for (int i = 0; i < 5; i++) {
      assertThat(verifier.verify("ORDX")).contains(t);
    }
    verify(client, times(1)).verify("ORDX");
  }

  @Test
  void notFoundIsCachedTooButErrorsAreNot() {
    when(client.verify("ORDNONE")).thenReturn(Optional.empty());
    verifier.verify("ORDNONE");
    verifier.verify("ORDNONE");
    verify(client, times(1)).verify("ORDNONE");

    when(client.verify("ORDDOWN")).thenThrow(new PaystackClient.PaystackException("down"));
    assertThatThrownBy(() -> verifier.verify("ORDDOWN")).isInstanceOf(PaystackClient.PaystackException.class);
    assertThatThrownBy(() -> verifier.verify("ORDDOWN")).isInstanceOf(PaystackClient.PaystackException.class);
    verify(client, times(2)).verify("ORDDOWN");
  }

  @Test
  void zeroTtlDisablesTheCache() {
    ReflectionTestUtils.setField(properties, "verifyCacheTtl", Duration.ZERO);
    when(client.verify("ORDX")).thenReturn(Optional.empty());

    verifier.verify("ORDX");
    verifier.verify("ORDX");

    verify(client, times(2)).verify("ORDX");
    assertThat(verifier.size()).isZero();
  }

  @Test
  void cacheStaysBounded() {
    when(client.verify(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
    for (int i = 0; i < PaystackVerifier.MAX_ENTRIES + 10; i++) {
      verifier.verify("ORD" + i);
    }
    assertThat(verifier.size()).isLessThanOrEqualTo(PaystackVerifier.MAX_ENTRIES);
  }
}
