package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.domain.Filters;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.model.AttemptState;
import it.gov.pagopa.nodetsworker.observability.model.LogicalState;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import jakarta.ws.rs.core.MultivaluedMap;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Strict query-parameter validation and window resolution, mirroring {@code query()}/{@code windowFor()}. */
public final class QuerySupport {

  public static final List<String> FILTERS = List.of("paFiscalCode", "noticeNumber", "logicalState",
      "attemptState", "serviceIdentifier", "psp", "canale", "stazione", "anomaliesOnly", "unresolvedOnly");
  public static final List<String> DIMENSIONS = List.of("serviceIdentifier", "psp", "canale", "stazione");

  private static final Pattern PA = Pattern.compile("^\\d{11}$");
  private static final Pattern NOTICE = Pattern.compile("^\\d{18}$");
  private static final Pattern PAYMENT_KEY_PA = Pattern.compile("[0-9]{11}");
  private static final Pattern PAYMENT_KEY_NOTICE = Pattern.compile("[0-9]{18}");

  private QuerySupport() {
    throw new IllegalStateException("Utility class");
  }

  public record PaymentKey(String paFiscalCode, String noticeNumber)
      implements Comparable<PaymentKey> {
    public PaymentKey {
      if (paFiscalCode == null || !PAYMENT_KEY_PA.matcher(paFiscalCode).matches()
          || noticeNumber == null || !PAYMENT_KEY_NOTICE.matcher(noticeNumber).matches()) {
        throw Errors.invalidRequest();
      }
    }

    @Override
    public int compareTo(PaymentKey other) {
      int paComparison = paFiscalCode.compareTo(other.paFiscalCode);
      return paComparison != 0 ? paComparison : noticeNumber.compareTo(other.noticeNumber);
    }
  }

  /** Parse payment-list keys, rejecting malformed or duplicate pairs and returning canonical order. */
  public static List<PaymentKey> parsePaymentKeys(String value) {
    if (value == null || value.isEmpty() || value.length() > 256
        || !value.chars().allMatch(character -> character <= 0x7f)) {
      throw Errors.invalidRequest();
    }

    List<PaymentKey> keys = new ArrayList<>();
    Set<PaymentKey> uniqueKeys = new HashSet<>();
    for (String element : value.split(",", -1)) {
      String[] parts = element.split(":", -1);
      if (parts.length != 2) {
        throw Errors.invalidRequest();
      }
      PaymentKey key = new PaymentKey(parts[0], parts[1]);
      if (!uniqueKeys.add(key)) {
        throw Errors.invalidRequest();
      }
      keys.add(key);
    }
    keys.sort(PaymentKey::compareTo);
    return List.copyOf(keys);
  }

  /** Render payment keys as a sorted canonical comma-separated value. */
  public static String formatPaymentKeys(List<PaymentKey> keys) {
    if (keys == null || keys.stream().anyMatch(key -> key == null)) {
      throw Errors.invalidRequest();
    }
    return new TreeSet<>(keys).stream()
        .map(key -> key.paFiscalCode() + ":" + key.noticeNumber())
        .collect(Collectors.joining(","));
  }

  /**
   * Validate each received query parameter and collapse it to a single value. Unknown names, repeated
   * values and out-of-range lengths/values all fail with {@code 400 INVALID_REQUEST}.
   */
  public static Map<String, String> validate(MultivaluedMap<String, String> params, Set<String> allowed) {
    Map<String, String> q = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : params.entrySet()) {
      String name = entry.getKey();
      List<String> values = entry.getValue();
      if (!allowed.contains(name) || values.size() != 1) {
        throw Errors.invalidRequest();
      }
      String value = values.get(0);
      if (value == null || value.isEmpty() || value.length() > 256) {
        throw Errors.invalidRequest();
      }
      q.put(name, value);
    }
    if (q.containsKey("logicalState") && !LogicalState.isValid(q.get("logicalState"))) {
      throw Errors.invalidRequest();
    }
    if (q.containsKey("attemptState") && !AttemptState.isValid(q.get("attemptState"))) {
      throw Errors.invalidRequest();
    }
    for (String flag : List.of("anomaliesOnly", "unresolvedOnly", "live")) {
      if (q.containsKey(flag) && !List.of("true", "false").contains(q.get(flag))) {
        throw Errors.invalidRequest();
      }
    }
    if (q.containsKey("paFiscalCode") && !PA.matcher(q.get("paFiscalCode")).matches()) {
      throw Errors.invalidRequest();
    }
    if (q.containsKey("noticeNumber") && !NOTICE.matcher(q.get("noticeNumber")).matches()) {
      throw Errors.invalidRequest();
    }
    return q;
  }

  /** A rolling (preset) window: open on the right, so activity after {@code to} still counts. */
  public static boolean live(Map<String, String> q) {
    return "true".equals(q.get("live"));
  }

  public static Filters filters(Map<String, String> q) {
    return Filters.builder()
        .paFiscalCode(q.get("paFiscalCode"))
        .noticeNumber(q.get("noticeNumber"))
        .logicalState(q.get("logicalState"))
        .attemptState(q.get("attemptState"))
        .serviceIdentifier(q.get("serviceIdentifier"))
        .psp(q.get("psp"))
        .canale(q.get("canale"))
        .stazione(q.get("stazione"))
        .anomaliesOnly(q.get("anomaliesOnly"))
        .unresolvedOnly(q.get("unresolvedOnly"))
        .build();
  }

  /** Reject aggregate requests with more than one attempt dimension, or a station without a creditor. */
  public static void checkAggregate(Map<String, String> q) {
    long dimensions = DIMENSIONS.stream().filter(q::containsKey).count();
    if (dimensions > 1 || (q.containsKey("stazione") && !q.containsKey("paFiscalCode"))) {
      throw Errors.unsupportedAggregation();
    }
  }

  /** Resolve and validate the half-open {@code [from, to)} window (default last hour, max 60 days). */
  public static Instant[] window(Map<String, String> q, Instant now, ObservabilityConfig config) {
    Instant from = parse(q.get("from"), now.minus(config.windowDefaultMinutes(), ChronoUnit.MINUTES));
    Instant to = parse(q.get("to"), now);
    long maxMs = config.windowMaxDays() * 24L * 60L * 60L * 1000L;
    if (!from.isBefore(to) || (to.toEpochMilli() - from.toEpochMilli()) > maxMs) {
      throw Errors.invalidRequest();
    }
    return new Instant[] {from, to};
  }

  private static Instant parse(String text, Instant fallback) {
    if (text == null) {
      return fallback;
    }
    Instant parsed = IsoTime.parse(text);
    if (parsed == null) {
      throw Errors.invalidRequest();
    }
    return parsed;
  }
}
