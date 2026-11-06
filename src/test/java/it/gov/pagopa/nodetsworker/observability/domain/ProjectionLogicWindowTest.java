package it.gov.pagopa.nodetsworker.observability.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.gov.pagopa.nodetsworker.observability.dto.AggregateDto;
import it.gov.pagopa.nodetsworker.observability.dto.StateCountDto;
import it.gov.pagopa.nodetsworker.observability.service.AggregationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Interval semantics of the aggregates, mirrored from {@code withinWindow()} in {@code mocks/model.mjs}. */
class ProjectionLogicWindowTest {

  private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
  private static final List<DomainPayment> ALL = Fixtures.build(NOW);

  private static Instant daysAgo(double days) {
    return NOW.minus(Duration.ofMillis((long) (days * 86_400_000L)));
  }

  private static List<String> attemptIds(List<DomainPayment> payments) {
    return payments.stream().flatMap(p -> p.getAttempts().stream()).map(DomainAttempt::getAttemptId).toList();
  }

  private static long count(List<StateCountDto> rows, String state) {
    return rows.stream().filter(r -> r.getState().equals(state)).mapToLong(StateCountDto::getCount).sum();
  }

  @Test
  void keepsOnlyEntitiesWithActivityInTheWindowWithTheirCurrentState() {
    List<DomainPayment> scoped = ProjectionLogic.withinWindow(ALL, daysAgo(30), NOW, false);

    assertEquals(6, scoped.size());
    // att_003_1 is 59.9 days old and the undated attempt of payment 8 matches no interval.
    assertEquals(List.of("att_001_1", "att_002_1", "att_002_2", "att_004_1", "att_005_1", "att_006_1", "att_007_1"),
        attemptIds(scoped));
    assertFalse(scoped.stream().anyMatch(p -> p.getNoticeNumber().endsWith("8")));
  }

  @Test
  void aPaymentCarriesOnlyItsActiveAttempts() {
    // Window around the second attempt of payment 2 only (it starts one day after the first).
    Instant start = daysAgo(2);
    List<DomainPayment> scoped = ProjectionLogic.withinWindow(ALL, start.minusSeconds(30), start.plusSeconds(30), false);

    DomainPayment payment2 = scoped.stream().filter(p -> p.getNoticeNumber().endsWith("2")).findFirst().orElseThrow();
    assertEquals(List.of("att_002_2"), payment2.getAttempts().stream().map(DomainAttempt::getAttemptId).toList());
  }

  @Test
  void aLiveWindowStaysOpenOnTheRight() {
    Instant from = daysAgo(30);
    Instant to = daysAgo(1.04);

    List<DomainPayment> closed = ProjectionLogic.withinWindow(ALL, from, to, false);
    List<DomainPayment> live = ProjectionLogic.withinWindow(ALL, from, to, true);

    assertEquals(4, closed.size());
    assertEquals(6, live.size());
    assertTrue(attemptIds(live).containsAll(List.of("att_001_1", "att_004_1")));
    assertFalse(attemptIds(closed).contains("att_001_1"));
  }

  @Test
  void theBoundsAreHalfOpen() {
    Instant start = ALL.get(0).getAttempts().get(0).getAttemptStartedAt();
    assertEquals(1, ProjectionLogic.withinWindow(ALL, start, start.plusSeconds(1), false).size());
    assertEquals(0, ProjectionLogic.withinWindow(ALL, start.minusSeconds(1), start, false).size());
  }

  @Test
  void everyCountOfTheAggregateFollowsTheWindow() {
    AggregateDto wide = AggregationService.aggregate(ALL, daysAgo(30), NOW, NOW, 1, false);
    AggregateDto narrow = AggregationService.aggregate(ALL, daysAgo(2.5), NOW, NOW, 1, false);
    AggregateDto empty = AggregationService.aggregate(ALL, NOW.minusSeconds(3600), NOW, NOW, 1, false);

    assertEquals(6, wide.getCounts().getPayments());
    assertEquals(7, wide.getCounts().getAttempts());
    assertEquals(2, wide.getUnresolved().getAttempts());
    assertTrue(narrow.getCounts().getAttempts() < wide.getCounts().getAttempts());
    assertEquals(narrow.getCounts().getAttempts(),
        narrow.getAttemptStates().stream().mapToLong(StateCountDto::getCount).sum());
    assertEquals(narrow.getCounts().getPayments(),
        narrow.getLogicalStates().stream().mapToLong(StateCountDto::getCount).sum());
    // PAYMENT_UNKNOWN (att_001_1) is one day old: in the 2.5-day window, not in the last hour.
    assertEquals(1, count(narrow.getAttemptStates(), "PAYMENT_UNKNOWN"));
    assertEquals(0, count(empty.getAttemptStates(), "PAYMENT_UNKNOWN"));
    assertEquals(0, empty.getCounts().getPayments());
    assertEquals(0, empty.getUnresolved().getAttempts());
    // The fully undated payment is in no window, so its anomaly is not counted either.
    assertEquals(0, wide.getAnomalies().getPayments());
  }
}
