package it.gov.pagopa.nodetsworker.observability.re;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReTimeTest {
  @Test
  void convertsUtcWindowToRomeWallClockInWinterAndSummer() {
    assertEquals("datetime(2026-11-02T10:43:31.172)", ReTime.localLiteral(Instant.parse("2026-11-02T09:43:31.172Z")));
    assertEquals("datetime(2026-08-02T11:43:31.172)", ReTime.localLiteral(Instant.parse("2026-08-02T09:43:31.172Z")));
    assertEquals("datetime(2026-11-02T10:00:00)", ReTime.localLiteral(Instant.parse("2026-11-02T09:00:00Z")));
  }

  @Test
  void convertsSourceLocalLabelledUtcBackToRealUtc() {
    assertEquals(Instant.parse("2026-11-02T09:58:30.928405Z"), ReTime.toUtc(Instant.parse("2026-11-02T10:58:30.928405Z")));
    assertEquals(Instant.parse("2026-10-08T15:54:22.748752Z"), ReTime.toUtc(Instant.parse("2026-10-08T17:54:22.748752Z")));
  }

  @Test
  void autumnOverlapUsesTheFirstOccurrenceAndSpringGapShiftsForward() {
    // 02:30 happens twice on 2026-10-25: first occurrence is CEST (UTC+2), as in EventTime.
    assertEquals(Instant.parse("2026-10-25T00:30:00Z"), ReTime.toUtc(Instant.parse("2026-10-25T02:30:00Z")));
    // 02:30 does not exist on 2026-03-29: the standard forward shift applies (03:30 CEST = 01:30Z).
    assertEquals(Instant.parse("2026-03-29T01:30:00Z"), ReTime.toUtc(Instant.parse("2026-03-29T02:30:00Z")));
  }

  @Test
  void roundTripsOutsideTheOverlap() {
    for (String utc : new String[] {"2026-01-15T12:00:00.123456789Z", "2026-07-15T12:00:00Z", "2026-10-25T05:00:00Z"}) {
      var instant = Instant.parse(utc);
      var local = ReTime.localLiteral(instant).replaceAll("^datetime\\((.*)\\)$", "$1") + "Z";
      assertEquals(instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).getEpochSecond(),
          ReTime.toUtc(Instant.parse(pad(local))).getEpochSecond());
    }
  }

  @Test
  void fieldConversionIsLosslessForNonTimestamps() {
    assertEquals(null, ReTime.fieldToUtc(null));
    assertEquals("not-a-date", ReTime.fieldToUtc("not-a-date"));
    assertEquals(42, ReTime.fieldToUtc(42));
    assertEquals("2026-11-02T09:58:30.928405Z", ReTime.fieldToUtc("2026-11-02T10:58:30.928405Z"));
  }

  private static String pad(String local) {
    return local.matches(".*T\\d\\d:\\d\\d:\\d\\d.*") ? local : local.replace("Z", ":00Z");
  }
}
