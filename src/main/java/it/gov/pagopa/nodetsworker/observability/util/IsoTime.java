package it.gov.pagopa.nodetsworker.observability.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * UTC timestamp formatting/parsing for the observability contract. Every API timestamp is
 * {@code yyyy-MM-ddTHH:mm:ss.SSSZ} (millisecond precision, Z suffix), as produced by the mock.
 */
public final class IsoTime {

  public static final Pattern UTC_MILLIS =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$");

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private IsoTime() {
    throw new IllegalStateException("Utility class");
  }

  /** Format an instant as a millisecond-precision UTC string, or {@code null} for a null instant. */
  public static String format(Instant instant) {
    return instant == null ? null : FORMATTER.format(instant);
  }

  /** Parse a strict millisecond-precision UTC string; returns {@code null} when the text is not valid. */
  public static Instant parse(String text) {
    if (text == null || !UTC_MILLIS.matcher(text).matches()) {
      return null;
    }
    try {
      Instant parsed = Instant.parse(text);
      return format(parsed).equals(text) ? parsed : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** The UTC day bucket ({@code yyyy-MM-ddT00:00:00.000Z}) an instant falls into. */
  public static String dayBucket(Instant instant) {
    return format(instant).substring(0, 10) + "T00:00:00.000Z";
  }

  /**
   * The aggregation bucket size for a window {@code [from, to)}, following the UI presets so the
   * number of buckets stays bounded (≤ ~60) and readable at every zoom level:
   *
   * <ul>
   *   <li>span ≤ 1 hour → 1 minute (5m/15m/30m/1h presets);</li>
   *   <li>span ≤ 24 hours → 1 hour;</li>
   *   <li>span ≤ 7 days → 6 hours;</li>
   *   <li>larger (up to the 60-day maximum) → 1 day.</li>
   * </ul>
   *
   * Thresholds are inclusive of the preset spans; custom windows pick the size by their duration.
   */
  public static Duration bucketSize(Instant from, Instant to) {
    long seconds = Math.max(1, Duration.between(from, to).getSeconds());
    if (seconds <= 3_600) {
      return Duration.ofMinutes(1);
    }
    if (seconds <= 86_400) {
      return Duration.ofHours(1);
    }
    if (seconds <= 604_800) {
      return Duration.ofHours(6);
    }
    return Duration.ofDays(1);
  }

  /**
   * The start of the {@code size}-wide bucket an instant falls into, floored on epoch-aligned
   * boundaries (minute/hour/6-hour/day boundaries are all multiples from the Unix epoch, which is
   * {@code 1970-01-01T00:00:00Z}), formatted as a millisecond-precision UTC string.
   */
  public static String bucket(Instant instant, Duration size) {
    long sizeSeconds = size.getSeconds();
    long floored = Math.floorDiv(instant.getEpochSecond(), sizeSeconds) * sizeSeconds;
    return format(Instant.ofEpochSecond(floored));
  }
}
