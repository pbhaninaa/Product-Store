package com.productstore.platform.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Booking fee rules: range (BR-1), rounding and remainder (NFR-1), the 24 hour refund boundary (BR-4, BR-5). */
class BookingFeePolicyTest {
  private static final Instant START = Instant.parse("2026-12-01T08:00:00Z");

  @ParameterizedTest
  @ValueSource(strings = {"0", "1", "20", "49", "50", "20.00", "50.0"})
  void acceptsWholePercentsFromZeroToFifty(String raw) {
    assertThat(BookingFeePolicy.validatePercent(new BigDecimal(raw))).isEqualTo(new BigDecimal(raw).intValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "-0.5", "50.01", "51", "60", "100", "12.5"})
  void rejectsPercentsOutsideTheRangeOrFractional(String raw) {
    assertThatThrownBy(() -> BookingFeePolicy.validatePercent(new BigDecimal(raw)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("booking_fee_percent_out_of_range");
  }

  @Test
  void rejectsAMissingPercent() {
    assertThatThrownBy(() -> BookingFeePolicy.validatePercent(null)).hasMessage("booking_fee_percent_out_of_range");
  }

  @Test
  void storedValuesOutsideTheRangeCountAsNoFee() {
    assertThat(BookingFeePolicy.effectivePercent(null)).isZero();
    assertThat(BookingFeePolicy.effectivePercent(-3)).isZero();
    assertThat(BookingFeePolicy.effectivePercent(51)).isZero();
    assertThat(BookingFeePolicy.effectivePercent(35)).isEqualTo(35);
  }

  @ParameterizedTest(name = "{1}% of R{0} = R{2}, remainder R{3}")
  @CsvSource({
    "1000.00, 20, 200.00, 800.00",
    "1000.00, 0, 0.00, 1000.00",
    "999.99, 50, 500.00, 499.99", // 499.995 rounds half up
    "333.33, 15, 50.00, 283.33", // 49.9995 rounds up to 50.00
    "10.05, 10, 1.01, 9.04", // 1.005 rounds half up
    "0.00, 20, 0.00, 0.00",
    "4.00, 20, 0.80, 3.20"
  })
  void feeIsRoundedToTheCentAndThePartsAddUpToTheTotal(String price, int percent, String fee, String remainder) {
    BigDecimal p = new BigDecimal(price);
    BigDecimal f = BookingFeePolicy.feeFor(p, percent);
    BigDecimal r = BookingFeePolicy.remainder(p, f);
    assertThat(f).isEqualByComparingTo(fee);
    assertThat(f.scale()).isEqualTo(2);
    assertThat(r).isEqualByComparingTo(remainder);
    assertThat(f.add(r)).isEqualByComparingTo(p);
  }

  @Test
  void noFeeForAMissingOrNegativePrice() {
    assertThat(BookingFeePolicy.feeFor(null, 20)).isEqualByComparingTo("0");
    assertThat(BookingFeePolicy.feeFor(new BigDecimal("-5"), 20)).isEqualByComparingTo("0");
  }

  @Test
  void cancellingAtLeastTwentyFourHoursAheadRefunds() {
    assertThat(BookingFeePolicy.customerCancellationRefunds(START, START.minus(Duration.ofHours(25)))).isTrue();
    // Edge case 2: exactly 24 hours is "at least 24 hours".
    assertThat(BookingFeePolicy.customerCancellationRefunds(START, START.minus(Duration.ofHours(24)))).isTrue();
  }

  @Test
  void cancellingLessThanTwentyFourHoursAheadKeepsTheFee() {
    assertThat(BookingFeePolicy.customerCancellationRefunds(START, START.minus(Duration.ofHours(24)).plusMillis(1)))
        .isFalse();
    assertThat(BookingFeePolicy.customerCancellationRefunds(START, START.minus(Duration.ofHours(23)))).isFalse();
    assertThat(BookingFeePolicy.customerCancellationRefunds(START, START.plusSeconds(1))).isFalse();
    assertThat(BookingFeePolicy.customerCancellationRefunds(null, START)).isFalse();
  }
}
