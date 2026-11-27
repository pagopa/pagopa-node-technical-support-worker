package it.gov.pagopa.nodetsworker.observability.ingestion;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Converts the RE {@code dataOraEvento} — a local {@code Europe/Rome} timestamp without zone info,
 * {@code yyyy-MM-dd'T'HH:mm:ss.SSS} — to the UTC {@link Instant} stored on the projection (spec §4.1).
 * Conversion uses the IANA {@code Europe/Rome} rules, not a fixed offset. DST edge cases:
 *
 * <ul>
 *   <li><b>Fall-back overlap</b> (last Sunday of October, 02:00–02:59 occurs twice): always the first
 *       occurrence (DST, UTC+2). An accepted discrepancy, not an anomaly.</li>
 *   <li><b>Spring-forward gap</b> (last Sunday of March, 02:00–02:59 does not exist): the standard
 *       forward shift is applied and a technical anomaly is flagged.</li>
 *   <li><b>Missing/unparseable</b>: {@code instant} is {@code null} with basis {@link
 *       TimestampBasis#ANOMALY}; the transition is still applied, only its time is absent.</li>
 * </ul>
 */
public final class EventTime {

  private static final ZoneId ROME = ZoneId.of("Europe/Rome");
  private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

  private EventTime() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * The resolved event time. {@code basis} is {@link TimestampBasis#SOURCE_EVENT} for a valid time
   * (even across the accepted fall-back overlap) and {@link TimestampBasis#ANOMALY} when the time is
   * missing or unparseable (then {@code instant} is {@code null}). {@code nonExistentLocalTime} is
   * {@code true} only for the spring-forward gap, which is a technical anomaly on a present time.
   */
  public record Resolved(Instant instant, TimestampBasis basis, boolean nonExistentLocalTime) {
  }

  public static Instant toInstant(LocalDateTime local) {
    if (local == null) {
      return null;
    }
    return local.atZone(ROME).withEarlierOffsetAtOverlap().toInstant();
  }

  public static Resolved resolve(String dataOraEvento) {
    if (dataOraEvento == null || dataOraEvento.isBlank()) {
      return new Resolved(null, TimestampBasis.ANOMALY, false);
    }
    LocalDateTime local;
    try {
      local = LocalDateTime.parse(dataOraEvento.trim(), FORMAT);
    } catch (RuntimeException e) {
      return new Resolved(null, TimestampBasis.ANOMALY, false);
    }
    // Detect the spring-forward gap: the local time maps to no valid offset at Europe/Rome.
    boolean nonExistent = ROME.getRules().getValidOffsets(local).isEmpty();
    // Overlap (fall-back): prefer the earlier offset (summer time, UTC+2) per the contract.
    ZonedDateTime zoned = local.atZone(ROME).withEarlierOffsetAtOverlap();
    Instant instant = zoned.toInstant();
    return new Resolved(instant, TimestampBasis.SOURCE_EVENT, nonExistent);
  }

  /** Current UTC instant helper, kept here so the ingestion never reaches for a non-UTC clock. */
  public static Instant nowUtc() {
    return Instant.now().atOffset(ZoneOffset.UTC).toInstant();
  }
}
