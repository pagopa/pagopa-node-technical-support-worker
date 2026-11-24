package it.gov.pagopa.nodetsworker.observability.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.util.List;
import java.util.Set;
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
  void acceptsAFullDefaultPageOfTwentyPaymentKeys() {
    String keys = paymentKeys(20);
    assertEquals(619, keys.length());
    assertEquals(20, QuerySupport.parsePaymentKeys(keys).size());
  }

  @Test
  void acceptsUpToTheMaximumPageSizeOfPaymentKeys() {
    assertEquals(QuerySupport.MAX_PAYMENT_KEYS,
        QuerySupport.parsePaymentKeys(paymentKeys(QuerySupport.MAX_PAYMENT_KEYS)).size());
  }

  @Test
  void rejectsMorePaymentKeysThanTheMaximumPageSize() {
    assertInvalid(paymentKeys(QuerySupport.MAX_PAYMENT_KEYS + 1));
  }

  @Test
  void rejectsValuesLongerThanTheMaximumKeyList() {
    assertInvalid(paymentKeys(QuerySupport.MAX_PAYMENT_KEYS) + "1");
  }

  @Test
  void otherQueryValuesKeepThe256CharacterLimit() {
    MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
    params.putSingle("psp", "x".repeat(257));
    assertThrows(ObservabilityException.class, () -> QuerySupport.validate(params, Set.of("psp")));
    params.putSingle("psp", "x".repeat(256));
    assertEquals("x".repeat(256), QuerySupport.validate(params, Set.of("psp")).get("psp"));
  }

  @Test
  void keysQueryValueAcceptsAFullPageButNotMore() {
    MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
    params.putSingle("keys", paymentKeys(QuerySupport.MAX_PAYMENT_KEYS));
    assertEquals(paymentKeys(QuerySupport.MAX_PAYMENT_KEYS),
        QuerySupport.validate(params, Set.of("keys")).get("keys"));
    params.putSingle("keys", paymentKeys(QuerySupport.MAX_PAYMENT_KEYS) + ",");
    assertThrows(ObservabilityException.class, () -> QuerySupport.validate(params, Set.of("keys")));
  }

  static String paymentKeys(int count) {
    StringBuilder keys = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        keys.append(',');
      }
      keys.append("12345678901:").append(String.format("3%017d", i));
    }
    return keys.toString();
  }

  private static void assertInvalid(String value) {
    assertThrows(ObservabilityException.class, () -> QuerySupport.parsePaymentKeys(value));
  }
}
