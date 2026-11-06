package it.gov.pagopa.nodetsworker.observability.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Europe/Rome -> UTC conversion and DST edge cases (spec §4.1). */
class EventTimeTest {

  @Test
  void convertsLocalDateTimeToInstant() {
    assertEquals(
        Instant.parse("2024-06-15T08:00:00Z"),
        EventTime.toInstant(LocalDateTime.parse("2024-06-15T10:00")));
  }

  @Test
  void convertsNullLocalDateTimeToNull() {
    assertNull(EventTime.toInstant(null));
  }

  @Test
  void localDateTimeOverlapPrefersEarlierOffset() {
    assertEquals(
        Instant.parse("2024-10-27T00:30:00Z"),
        EventTime.toInstant(LocalDateTime.parse("2024-10-27T02:30")));
  }

  @Test
  void localDateTimeGapShiftsForward() {
    assertEquals(
        Instant.parse("2024-03-31T01:30:00Z"),
        EventTime.toInstant(LocalDateTime.parse("2024-03-31T02:30")));
  }

  @Test
  void convertsSummerTimeToUtc() {
    EventTime.Resolved r = EventTime.resolve("2024-06-15T10:30:00.000");
    assertEquals(Instant.parse("2024-06-15T08:30:00Z"), r.instant()); // CEST = UTC+2
    assertEquals(TimestampBasis.SOURCE_EVENT, r.basis());
    assertFalse(r.nonExistentLocalTime());
  }

  @Test
  void convertsWinterTimeToUtc() {
    EventTime.Resolved r = EventTime.resolve("2024-01-15T10:30:00.000");
    assertEquals(Instant.parse("2024-01-15T09:30:00Z"), r.instant()); // CET = UTC+1
    assertEquals(TimestampBasis.SOURCE_EVENT, r.basis());
  }

  @Test
  void fallBackOverlapPrefersEarlierOffset() {
    // 2024-10-27 02:30 occurs twice; the contract picks the first occurrence (summer time, UTC+2).
    EventTime.Resolved r = EventTime.resolve("2024-10-27T02:30:00.000");
    assertEquals(Instant.parse("2024-10-27T00:30:00Z"), r.instant());
    assertEquals(TimestampBasis.SOURCE_EVENT, r.basis());
    assertFalse(r.nonExistentLocalTime());
  }

  @Test
  void springForwardGapIsFlaggedAnomaly() {
    // 2024-03-31 02:30 does not exist; the forward shift is applied and an anomaly is flagged.
    EventTime.Resolved r = EventTime.resolve("2024-03-31T02:30:00.000");
    assertTrue(r.nonExistentLocalTime());
    assertEquals(Instant.parse("2024-03-31T01:30:00Z"), r.instant()); // shifted to 03:30 CEST
    assertEquals(TimestampBasis.SOURCE_EVENT, r.basis());
  }

  @Test
  void missingTimeIsAnomaly() {
    EventTime.Resolved r = EventTime.resolve(null);
    assertNull(r.instant());
    assertEquals(TimestampBasis.ANOMALY, r.basis());
  }

  @Test
  void unparseableTimeIsAnomaly() {
    EventTime.Resolved r = EventTime.resolve("not-a-timestamp");
    assertNull(r.instant());
    assertEquals(TimestampBasis.ANOMALY, r.basis());
  }
}
