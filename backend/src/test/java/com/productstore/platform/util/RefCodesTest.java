package com.productstore.platform.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class RefCodesTest {
  private static final UUID ID = UUID.fromString("3f9a12bc-1d2e-4f50-8a9b-0c1d2e3f4a5b");

  @Test
  void shortRefIsFirstEightHexUpperCase() {
    assertThat(RefCodes.shortRef(ID)).isEqualTo("3F9A12BC");
    assertThat(RefCodes.shortRef(null)).isEmpty();
  }

  @Test
  void shortRefMatchesAnyCaseSpacingOrHash() {
    assertThat(RefCodes.isShortRefOf("3f9a12bc", ID)).isTrue();
    assertThat(RefCodes.isShortRefOf("#3F9A-12BC", ID)).isTrue();
    assertThat(RefCodes.isShortRefOf("3F9A12BD", ID)).isFalse();
    assertThat(RefCodes.isShortRef("not-a-ref")).isFalse();
  }

  @Test
  void bankReferenceAcceptsShortOrFullId() {
    assertThat(RefCodes.bankReferenceMentions("Payment 3F9A12BC", ID)).isTrue();
    assertThat(RefCodes.bankReferenceMentions(ID.toString(), ID)).isTrue();
    assertThat(RefCodes.bankReferenceMentions("3f9a12bc1d2e4f508a9b0c1d2e3f4a5b", ID)).isTrue();
    assertThat(RefCodes.bankReferenceMentions("Payment for order", ID)).isFalse();
    assertThat(RefCodes.bankReferenceMentions("", ID)).isFalse();
  }

  @Test
  void parseUuidOrNullNeverThrows() {
    assertThat(RefCodes.parseUuidOrNull(ID.toString())).isEqualTo(ID);
    assertThat(RefCodes.parseUuidOrNull("3F9A12BC")).isNull();
    assertThat(RefCodes.parseUuidOrNull(null)).isNull();
  }
}
