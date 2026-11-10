package it.gov.pagopa.nodetsworker.observability.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuerySupportTest {

  @Test
  void parsesOnePaymentKey() {
    assertEquals(
        List.of(new QuerySupport.PaymentKey("12345678901", "123456789012345678")),
        QuerySupport.parsePaymentKeys("12345678901:123456789012345678"));
  }

  @Test
  void sortsMultiplePaymentKeysAndFormatsCanonically() {
    List<QuerySupport.PaymentKey> keys = QuerySupport.parsePaymentKeys(
        "99999999999:999999999999999999,12345678901:123456789012345678");

    assertEquals(
        List.of(
            new QuerySupport.PaymentKey("12345678901", "123456789012345678"),
            new QuerySupport.PaymentKey("99999999999", "999999999999999999")),
        keys);
    assertEquals(
        "12345678901:123456789012345678,99999999999:999999999999999999",
        QuerySupport.formatPaymentKeys(List.of(keys.get(1), keys.get(0))));
  }

  @Test
  void rejectsEmptyInputAndElements() {
    assertInvalid("");
    assertInvalid(",12345678901:123456789012345678");
    assertInvalid("12345678901:123456789012345678,");
    assertInvalid("12345678901:123456789012345678,,99999999999:999999999999999999");
  }

  @Test
  void rejectsMalformedColonStructure() {
    assertInvalid("12345678901123456789012345678");
    assertInvalid("12345678901::123456789012345678");
    assertInvalid(":123456789012345678");
    assertInvalid("12345678901:");
    assertInvalid("12345678901:123456789012345678:extra");
  }

  @Test
  void rejectsNonAsciiAndNondigitValues() {
    assertInvalid("1234567890a:123456789012345678");
    assertInvalid("12345678901:12345678901234567x");
    assertInvalid("１２３４５６７８９０１:123456789012345678");
    assertInvalid("12345678901:１２３４５６７８９０１２３４５６７８");
  }

  @Test
  void rejectsInvalidPairLengths() {
    assertInvalid("1234567890:123456789012345678");
    assertInvalid("123456789012:123456789012345678");
    assertInvalid("12345678901:12345678901234567");
    assertInvalid("12345678901:1234567890123456789");
  }

  @Test
  void rejectsDuplicatePaymentKeys() {
    assertInvalid(
        "12345678901:123456789012345678,12345678901:123456789012345678");
  }

  @Test
  void rejectsValuesLongerThan256Characters() {
    String tooLong = "12345678901:123456789012345678,"
        + "99999999999:999999999999999999,"
        + "88888888888:888888888888888888,"
        + "77777777777:777777777777777777,"
        + "66666666666:666666666666666666,"
        + "55555555555:555555555555555555,"
        + "44444444444:444444444444444444,"
        + "33333333333:333333333333333333,"
        + "22222222222:222222222222222222,"
        + "11111111111:111111111111111111";
    assertEquals(309, tooLong.length());
    assertInvalid(tooLong);
  }

  private static void assertInvalid(String value) {
    assertThrows(ObservabilityException.class, () -> QuerySupport.parsePaymentKeys(value));
  }
}
