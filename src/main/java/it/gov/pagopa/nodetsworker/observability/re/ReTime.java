package it.gov.pagopa.nodetsworker.observability.re;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The RE stores {@code insertedTimestamp} and {@code dataOraEvento} as {@code Europe/Rome} local times
 * without offset, which ADX nevertheless surfaces as datetimes (rendered with a {@code Z} suffix).
 * Windows and seek keys of the diagnostic are real UTC instants: they are converted to the local
 * representation before querying, and the timestamps read back are converted to UTC. Same IANA rules
 * and overlap policy as {@code EventTime}: during the autumn overlap the first occurrence (UTC+2) wins.
 */
final class ReTime {
  static final ZoneId SOURCE_ZONE = ZoneId.of("Europe/Rome");
  /** Scalar fields of the RE event that carry a source-local timestamp. */
  static final java.util.Set<String> LOCAL_FIELDS = java.util.Set.of("insertedTimestamp", "dataOraEvento");

  private ReTime() { }

  /** KQL datetime literal holding the Rome wall-clock time of a UTC instant. */
  static String localLiteral(Instant utc) {
    return "datetime(" + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(utc, SOURCE_ZONE)) + ")";
  }

  /** The UTC instant of a source datetime read as Rome wall-clock time (the {@code Z} is a mislabel). */
  static Instant toUtc(Instant sourceLocalLabelledUtc) {
    return LocalDateTime.ofInstant(sourceLocalLabelledUtc, ZoneOffset.UTC)
        .atZone(SOURCE_ZONE).withEarlierOffsetAtOverlap().toInstant();
  }

  /**
   * Converts a source-local scalar to a UTC ISO string. Values that are not parseable instants are
   * returned untouched: the diagnostic stays lossless rather than failing on one malformed field.
   */
  static Object fieldToUtc(Object value) {
    if (!(value instanceof String text)) {
      return value;
    }
    try {
      return toUtc(Instant.parse(text)).toString();
    } catch (DateTimeParseException unparseable) {
      return value;
    }
  }
}
