package it.gov.pagopa.nodetsworker.observability.repository;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.observability.domain.DomainAttempt;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.DomainRecipient;
import it.gov.pagopa.nodetsworker.observability.domain.ProjectionLogic;
import it.gov.pagopa.nodetsworker.observability.domain.TimelineItem;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import it.gov.pagopa.nodetsworker.observability.repository.models.PaymentEntity;
import it.gov.pagopa.nodetsworker.observability.service.ProjectionMapper;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;

/**
 * The projection graph is read with a constant number of statements per keyset page, never one statement
 * per payment, attempt or recipient, and the result is identical to the entity-by-entity mapping.
 */
@QuarkusTest
class ProjectionRepositoryLoadTest {

  private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
  private static final int STATEMENTS_PER_PAGE = 6;

  @Inject ProjectionRepository repository;

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  @ConfigProperty(name = "observability.projection.page-size")
  int pageSize;

  @Test
  @TestTransaction
  void loadsTheWholeGraphWithConstantStatementsPerPageAndParityWithTheEntityMapping() {
    String pa = "91000000001";
    int payments = pageSize * 2 + 3;
    for (int i = 0; i < payments; i++) {
      repository.persist(ProjectionMapper.toEntity(payment(pa, i, T0.plus(i, ChronoUnit.MINUTES))));
    }
    em.flush();
    em.clear();
    List<DomainPayment> expected = legacyLoad(pa);
    em.clear();

    Statistics statistics = statistics();
    List<DomainPayment> loaded = repository.loadVisible(pa);
    long statements = statistics.getPrepareStatementCount();

    long pages = payments / pageSize + 1;
    assertEquals(pages * STATEMENTS_PER_PAGE, statements,
        "statements must depend on the number of pages, not on the payments/attempts/recipients");
    assertEquals(sortedByKey(expected), loaded, "same graph, children order included, keyset order");
    assertEquals(payments, Set.copyOf(loaded.stream().map(DomainPayment::key).toList()).size(),
        "no payment lost or duplicated across page boundaries");
    assertEquals(3, loaded.get(0).getAttempts().get(1).getTimeline().size());
    assertEquals("NOTICE_SENT", loaded.get(0).getRecipients().get(0).getTimeline().get(1).getState());
  }

  @Test
  @TestTransaction
  void windowedLoadIsAnExactSupersetOfTheInMemoryWindowSelection() {
    String pa = "91000000002";
    Instant from = T0;
    Instant to = T0.plus(1, ChronoUnit.HOURS);
    // 0: payment transition inside; 1: everything before; 2: attempt start inside, no transition inside;
    // 3: only a recipient transition inside; 4: only an ANOMALY transition inside; 5: after the window.
    List<DomainPayment> all = new ArrayList<>();
    all.add(payment(pa, 0, from.plusSeconds(60)));
    all.add(payment(pa, 1, from.minus(2, ChronoUnit.DAYS)));
    DomainPayment startOnly = payment(pa, 2, from.minus(2, ChronoUnit.DAYS));
    startOnly.getAttempts().get(0).setAttemptStartedAt(from.plusSeconds(30));
    all.add(startOnly);
    DomainPayment recipientOnly = payment(pa, 3, from.minus(2, ChronoUnit.DAYS));
    recipientOnly.getRecipients().get(0).getTimeline().get(1).setTransitionAt(from.plusSeconds(10));
    all.add(recipientOnly);
    DomainPayment anomalyOnly = payment(pa, 4, from.minus(2, ChronoUnit.DAYS));
    anomalyOnly.getTimeline().add(new TimelineItem("PAID", from.plusSeconds(5), TimestampBasis.ANOMALY, "x"));
    all.add(anomalyOnly);
    all.add(payment(pa, 5, to.plusSeconds(1)));
    for (DomainPayment p : all) {
      repository.persist(ProjectionMapper.toEntity(p));
    }
    em.flush();
    em.clear();

    List<DomainPayment> full = repository.loadVisible(pa);
    for (boolean live : new boolean[] {false, true}) {
      List<DomainPayment> windowed = repository.loadActiveIn(pa, from, to, live);
      assertEquals(ProjectionLogic.withinWindow(full, from, to, live),
          ProjectionLogic.withinWindow(windowed, from, to, live));
      assertEquals(live ? List.of(0, 2, 3, 5) : List.of(0, 2, 3), indexes(windowed));
    }
  }

  @Test
  @TestTransaction
  void loadsOnlyTheRequestedKeys() {
    String pa = "91000000003";
    for (int i = 0; i < 4; i++) {
      repository.persist(ProjectionMapper.toEntity(payment(pa, i, T0)));
    }
    em.flush();
    em.clear();

    List<DomainPayment> loaded = repository.loadByKeys(List.of(
        new ProjectionRepository.PaymentKey(pa, notice(1)),
        new ProjectionRepository.PaymentKey(pa, notice(3)),
        new ProjectionRepository.PaymentKey(pa, notice(9))));

    assertEquals(List.of(1, 3), indexes(loaded));
  }

  private List<DomainPayment> legacyLoad(String pa) {
    List<DomainPayment> out = new ArrayList<>();
    for (PaymentEntity entity : em.createQuery(
        "SELECT p FROM PaymentEntity p WHERE p.paFiscalCode = :pa", PaymentEntity.class)
        .setParameter("pa", pa).getResultList()) {
      out.add(ProjectionMapper.toDomain(entity));
    }
    return out;
  }

  private Statistics statistics() {
    Statistics statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
    return statistics;
  }

  private static List<DomainPayment> sortedByKey(List<DomainPayment> payments) {
    List<DomainPayment> sorted = new ArrayList<>(payments);
    sorted.sort(Comparator.comparing(DomainPayment::getNoticeNumber));
    return sorted;
  }

  private static List<Integer> indexes(List<DomainPayment> payments) {
    return payments.stream().map(p -> Integer.parseInt(p.getNoticeNumber().substring(10))).sorted().toList();
  }

  private static String notice(int index) {
    return String.format("3000000000%08d", index);
  }

  private static DomainPayment payment(String pa, int index, Instant at) {
    String notice = notice(index);
    List<DomainAttempt> attempts = new ArrayList<>();
    for (int a = 0; a < 2; a++) {
      List<TimelineItem> timeline = new ArrayList<>();
      List<String> states = a == 0 ? List.of("PAYING", "CANCELLED") : List.of("PAYING", "PAYMENT_SENT", "PAID");
      for (int s = 0; s < states.size(); s++) {
        timeline.add(new TimelineItem(states.get(s), at.plusSeconds(s), TimestampBasis.SOURCE_EVENT, "t" + s));
      }
      attempts.add(DomainAttempt.builder()
          .attemptId(pa + "-" + notice + "-" + a).paymentToken("tok-" + notice + "-" + a)
          .serviceIdentifier("NDP004").psp("PSP" + a).stazione("ST" + a).canale("CH" + a)
          .attemptStartedAt(at).startTimestampBasis(TimestampBasis.SOURCE_EVENT)
          .firstSeenAt(at).expiresAt(at.plus(60, ChronoUnit.DAYS)).timeline(timeline).build());
    }
    List<TimelineItem> recipientTimeline = new ArrayList<>(List.of(
        new TimelineItem("NOTICE_GENERATED", at, TimestampBasis.SOURCE_EVENT, "actualGenerate"),
        new TimelineItem("NOTICE_SENT", at.plusSeconds(2), TimestampBasis.SOURCE_EVENT, "actualSend")));
    List<DomainRecipient> recipients = new ArrayList<>(List.of(DomainRecipient.builder()
        .recipientId(pa + "-" + notice + "-r").stazioneRecipient("STR").firstSeenAt(at)
        .expiresAt(at.plus(60, ChronoUnit.DAYS)).timeline(recipientTimeline).build()));
    List<TimelineItem> logical = new ArrayList<>(List.of(
        new TimelineItem("PAYING", at, TimestampBasis.SOURCE_EVENT, "activate"),
        new TimelineItem("PAID", at.plusSeconds(3), TimestampBasis.INFERRED, "outcome")));
    return DomainPayment.builder().paFiscalCode(pa).noticeNumber(notice).projectionRevision(index + 1L)
        .timeline(logical).attempts(attempts).recipients(recipients).build();
  }
}
