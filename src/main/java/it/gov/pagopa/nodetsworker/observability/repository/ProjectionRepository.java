package it.gov.pagopa.nodetsworker.observability.repository;

import io.quarkus.hibernate.orm.PersistenceUnit;
import it.gov.pagopa.nodetsworker.observability.domain.DomainAttempt;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.DomainRecipient;
import it.gov.pagopa.nodetsworker.observability.domain.TimelineItem;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveBucket;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveDirection;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveLogic;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveWarningRow;
import it.gov.pagopa.nodetsworker.observability.domain.DbCheckContext;
import it.gov.pagopa.nodetsworker.observability.re.ReContext;
import it.gov.pagopa.nodetsworker.observability.ingestion.IngestionProjectionWriter;
import it.gov.pagopa.nodetsworker.observability.repository.models.AttemptEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.AuditRecordEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.OutboxEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PaymentEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PrimitiveCountEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PrimitiveWarningEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.ProjectionMetaEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.RecipientEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Data access over the observability projection (second PostgreSQL datasource / persistence unit).
 * Reads assemble the in-memory domain graph from scalar projections, a bounded keyset page at a time.
 */
@ApplicationScoped
public class ProjectionRepository {

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  @Inject
  IngestionProjectionWriter writer;

  /** Payment natural key, used to reload only the payments touched by an outbox batch. */
  public record PaymentKey(String paFiscalCode, String noticeNumber) {
  }

  /**
   * Keyset page size of every projection read. Each page costs a constant number of statements (one for
   * the payments, one per child collection) whatever the number of attempts, recipients and transitions.
   */
  @ConfigProperty(name = "observability.projection.page-size", defaultValue = "500")
  int pageSize = 500;

  /** Load the visible projection, optionally restricted to a single creditor. */
  @Transactional
  public List<DomainPayment> loadVisible(String allowedPa) {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new LinkedHashMap<>();
    restrictToPa(where, params, allowedPa);
    return scan(where.toString(), params);
  }

  /**
   * Load only the payments with activity in {@code [from, to)} (open on the right when {@code live}): a
   * non-ANOMALY transition of the payment, of an attempt or of a recipient, or an attempt start. It is the
   * same rule as {@link it.gov.pagopa.nodetsworker.observability.domain.ProjectionLogic#withinWindow}, so
   * the windowed aggregates are identical to those computed over the whole projection.
   */
  @Transactional
  public List<DomainPayment> loadActiveIn(String allowedPa, Instant from, Instant to, boolean live) {
    StringBuilder where = new StringBuilder();
    Map<String, Object> params = new LinkedHashMap<>();
    restrictToPa(where, params, allowedPa);
    String upper = live ? "" : " AND %1$s < :to";
    where.append(" AND (p.id IN (SELECT t.payment.id FROM LogicalTransitionEntity t WHERE ")
        .append(transitionInWindow("t", upper))
        .append(") OR p.id IN (SELECT a.payment.id FROM AttemptEntity a WHERE a.attemptStartedAt >= :from")
        .append(String.format(upper, "a.attemptStartedAt"))
        .append(") OR p.id IN (SELECT t.attempt.payment.id FROM AttemptTransitionEntity t WHERE ")
        .append(transitionInWindow("t", upper))
        .append(") OR p.id IN (SELECT t.recipient.payment.id FROM RecipientTransitionEntity t WHERE ")
        .append(transitionInWindow("t", upper))
        .append("))");
    params.put("from", from);
    params.put("anomaly", TimestampBasis.ANOMALY);
    if (!live) {
      params.put("to", to);
    }
    return scan(where.toString(), params);
  }

  /** Load the given payments (absent keys are simply missing from the result), sorted by key. */
  @Transactional
  public List<DomainPayment> loadByKeys(Collection<PaymentKey> keys) {
    List<PaymentKey> distinct = new ArrayList<>(new LinkedHashSet<>(keys));
    List<DomainPayment> out = new ArrayList<>();
    for (int start = 0; start < distinct.size(); start += pageSize) {
      List<PaymentKey> chunk = distinct.subList(start, Math.min(distinct.size(), start + pageSize));
      Set<PaymentKey> wanted = new HashSet<>(chunk);
      // Each column is matched independently and the exact pairs are kept below: portable and index-friendly.
      List<Object[]> rows = new ArrayList<>(em.createQuery(PAYMENT_COLUMNS
              + " WHERE p.paFiscalCode IN :pas AND p.noticeNumber IN :notices ORDER BY p.id", Object[].class)
          .setParameter("pas", chunk.stream().map(PaymentKey::paFiscalCode).collect(Collectors.toSet()))
          .setParameter("notices", chunk.stream().map(PaymentKey::noticeNumber).collect(Collectors.toSet()))
          .getResultList());
      rows.removeIf(r -> !wanted.contains(new PaymentKey((String) r[1], (String) r[2])));
      out.addAll(hydrate(rows));
    }
    out.sort(Comparator.comparing(DomainPayment::key));
    return out;
  }

  private static void restrictToPa(StringBuilder where, Map<String, Object> params, String allowedPa) {
    if (allowedPa != null) {
      where.append(" AND p.paFiscalCode = :pa");
      params.put("pa", allowedPa);
    }
  }

  private static String transitionInWindow(String alias, String upper) {
    String column = alias + ".transitionAt";
    return alias + ".timestampBasis <> :anomaly AND " + column + " >= :from" + String.format(upper, column);
  }

  private static final String PAYMENT_COLUMNS =
      "SELECT p.id, p.paFiscalCode, p.noticeNumber, p.projectionRevision, p.timelineIncomplete FROM PaymentEntity p";

  /** Keyset scan over the payment id: bounded statements, no OFFSET, no entity kept in the persistence context. */
  private List<DomainPayment> scan(String where, Map<String, Object> params) {
    List<DomainPayment> out = new ArrayList<>();
    long after = Long.MIN_VALUE;
    while (true) {
      var query = em.createQuery(PAYMENT_COLUMNS + " WHERE p.id > :after" + where + " ORDER BY p.id",
              Object[].class)
          .setParameter("after", after)
          .setMaxResults(pageSize);
      params.forEach(query::setParameter);
      List<Object[]> rows = query.getResultList();
      out.addAll(hydrate(rows));
      if (rows.size() < pageSize) {
        return out;
      }
      after = (Long) rows.get(rows.size() - 1)[0];
    }
  }

  /**
   * Build the domain graph of a page of payments with one statement per child collection. Children keep
   * the {@code @OrderColumn} order ({@code INDEX()}); collections are never fetch-joined together, so no
   * cartesian product is produced.
   */
  private List<DomainPayment> hydrate(List<Object[]> paymentRows) {
    if (paymentRows.isEmpty()) {
      return List.of();
    }
    Map<Long, DomainPayment> payments = new LinkedHashMap<>();
    for (Object[] r : paymentRows) {
      payments.put((Long) r[0], DomainPayment.builder()
          .paFiscalCode((String) r[1]).noticeNumber((String) r[2])
          .projectionRevision((Long) r[3]).timelineIncomplete((Boolean) r[4]).build());
    }
    List<Long> ids = new ArrayList<>(payments.keySet());

    for (Object[] r : children("SELECT p.id, " + TRANSITION_COLUMNS
        + " FROM PaymentEntity p JOIN p.timeline t WHERE p.id IN :ids ORDER BY p.id, INDEX(t)", ids)) {
      payments.get((Long) r[0]).getTimeline().add(timelineItem(r));
    }

    Map<Long, DomainAttempt> attempts = new HashMap<>();
    for (Object[] r : children("SELECT p.id, a.id, a.attemptId, a.paymentToken, a.serviceIdentifier, a.psp,"
        + " a.stazione, a.canale, a.attemptStartedAt, a.startTimestampBasis, a.firstSeenAt, a.expiresAt,"
        + " a.timelineIncomplete FROM PaymentEntity p JOIN p.attempts a WHERE p.id IN :ids"
        + " ORDER BY p.id, INDEX(a)", ids)) {
      DomainAttempt attempt = DomainAttempt.builder()
          .attemptId((String) r[2]).paymentToken((String) r[3]).serviceIdentifier((String) r[4])
          .psp((String) r[5]).stazione((String) r[6]).canale((String) r[7])
          .attemptStartedAt((Instant) r[8]).startTimestampBasis((TimestampBasis) r[9])
          .firstSeenAt((Instant) r[10]).expiresAt((Instant) r[11]).timelineIncomplete((Boolean) r[12])
          .build();
      attempts.put((Long) r[1], attempt);
      payments.get((Long) r[0]).getAttempts().add(attempt);
    }
    if (!attempts.isEmpty()) {
      for (Object[] r : children("SELECT a.id, " + TRANSITION_COLUMNS + " FROM PaymentEntity p JOIN p.attempts a"
          + " JOIN a.timeline t WHERE p.id IN :ids ORDER BY a.id, INDEX(t)", ids)) {
        attempts.get((Long) r[0]).getTimeline().add(timelineItem(r));
      }
    }

    Map<Long, DomainRecipient> recipients = new HashMap<>();
    for (Object[] r : children("SELECT p.id, r.id, r.recipientId, r.stazioneRecipient, r.firstSeenAt,"
        + " r.expiresAt, r.timelineIncomplete FROM PaymentEntity p JOIN p.recipients r WHERE p.id IN :ids"
        + " ORDER BY p.id, INDEX(r)", ids)) {
      DomainRecipient recipient = DomainRecipient.builder()
          .recipientId((String) r[2]).stazioneRecipient((String) r[3]).firstSeenAt((Instant) r[4])
          .expiresAt((Instant) r[5]).timelineIncomplete((Boolean) r[6]).build();
      recipients.put((Long) r[1], recipient);
      payments.get((Long) r[0]).getRecipients().add(recipient);
    }
    if (!recipients.isEmpty()) {
      for (Object[] r : children("SELECT r.id, " + TRANSITION_COLUMNS + " FROM PaymentEntity p JOIN p.recipients r"
          + " JOIN r.timeline t WHERE p.id IN :ids ORDER BY r.id, INDEX(t)", ids)) {
        recipients.get((Long) r[0]).getTimeline().add(timelineItem(r));
      }
    }
    return new ArrayList<>(payments.values());
  }

  private static final String TRANSITION_COLUMNS = "t.state, t.transitionAt, t.timestampBasis, t.trigger";

  private List<Object[]> children(String jpql, List<Long> ids) {
    return em.createQuery(jpql, Object[].class).setParameter("ids", ids).getResultList();
  }

  private static TimelineItem timelineItem(Object[] r) {
    return new TimelineItem((String) r[1], (Instant) r[2], (TimestampBasis) r[3], (String) r[4]);
  }

  @Transactional
  public List<String> distinctDimension(String column, String allowedPa) {
    String jpql = "SELECT DISTINCT a." + column + " FROM AttemptEntity a"
        + (allowedPa != null ? " WHERE a.payment.paFiscalCode = :pa" : "");
    var query = em.createQuery(jpql, String.class);
    if (allowedPa != null) {
      query.setParameter("pa", allowedPa);
    }
    return query.getResultList();
  }

  /** Indexed lookup without loading timelines, auditing tokens or sweeping retention. */
  @Transactional
  public Optional<DbCheckContext> findDbCheckContext(String attemptId, String allowedPa, Instant now) {
    var query = em.createQuery(
        "SELECT a FROM AttemptEntity a JOIN FETCH a.payment p"
            + " WHERE a.attemptId = :id AND a.expiresAt > :now"
            + (allowedPa == null ? "" : " AND p.paFiscalCode = :pa"), AttemptEntity.class)
        .setParameter("id", attemptId)
        .setParameter("now", now);
    if (allowedPa != null) {
      query.setParameter("pa", allowedPa);
    }
    return query.getResultList().stream().findFirst().map(attempt -> {
      PaymentEntity payment = attempt.getPayment();
      return new DbCheckContext(attempt.getAttemptId(), payment.getPaFiscalCode(), payment.getNoticeNumber(),
          attempt.getPaymentToken(), attempt.getServiceIdentifier(), payment.getCurrentState(),
          attempt.getCurrentState(), payment.getProjectionRevision(), attempt.getExpiresAt());
    });
  }

  // --- seeding / writes -----------------------------------------------------

  /** Retains expiry in the captured result so RE can distinguish expired from missing attempts. */
  @Transactional
  public Optional<ReContext> findReContext(String attemptId, String allowedPa) {
    var query = em.createQuery(
        "SELECT a FROM AttemptEntity a JOIN FETCH a.payment p WHERE a.attemptId = :id"
            + (allowedPa == null ? "" : " AND p.paFiscalCode = :pa"), AttemptEntity.class)
        .setParameter("id", attemptId);
    if (allowedPa != null) {
      query.setParameter("pa", allowedPa);
    }
    return query.getResultList().stream().findFirst().map(a ->
        new ReContext(a.getAttemptId(), a.getPayment().getPaFiscalCode(),
            a.getPayment().getNoticeNumber(), a.getPaymentToken(), a.getExpiresAt()));
  }

  @Transactional
  public long countPayments() {
    return em.createQuery("SELECT COUNT(p) FROM PaymentEntity p", Long.class).getSingleResult();
  }

  @Transactional
  public boolean existsPayment(String paFiscalCode, String noticeNumber) {
    return em.createQuery(
            "SELECT COUNT(p) FROM PaymentEntity p WHERE p.paFiscalCode = :pa AND p.noticeNumber = :notice",
            Long.class)
        .setParameter("pa", paFiscalCode)
        .setParameter("notice", noticeNumber)
        .getSingleResult() > 0;
  }

  @Transactional
  public boolean existsCreditor(String paFiscalCode) {
    return !em.createQuery("SELECT p.id FROM PaymentEntity p WHERE p.paFiscalCode = :pa", Long.class)
        .setParameter("pa", paFiscalCode)
        .setMaxResults(1)
        .getResultList().isEmpty();
  }

  @Transactional
  public void persist(PaymentEntity payment) {
    em.persist(payment);
  }

  // --- metadata -------------------------------------------------------------

  @Transactional
  public ProjectionMetaEntity meta() {
    ProjectionMetaEntity meta = em.find(ProjectionMetaEntity.class, 1);
    if (meta == null) {
      meta = ProjectionMetaEntity.builder()
          .id(1).aggregateRevision(1).lastSequence(0)
          .freshnessStatus("CURRENT").freshnessLagSeconds(0).freshnessGapsDetected(false)
          .lastProcessedAt(Instant.now()).lastPublishedAt(Instant.now())
          .build();
      em.persist(meta);
    }
    return meta;
  }

  @Transactional
  public long aggregateRevision() {
    return meta().getAggregateRevision();
  }

  // --- audit ----------------------------------------------------------------

  @Transactional
  public void insertAudit(AuditRecordEntity record) {
    em.persist(record);
  }

  @Transactional
  public List<AuditRecordEntity> audits() {
    return em.createQuery("SELECT a FROM AuditRecordEntity a ORDER BY a.id", AuditRecordEntity.class)
        .getResultList();
  }

  // --- analytics ------------------------------------------------------------

  /** Attempt dimensions plus the number of attempts that entered {@code PAYING} through one trigger. */
  public record ActivationGroup(String trigger, String serviceIdentifier, String psp, String canale,
      String stazione, long attempts) {
  }

  /**
   * Attempts whose {@code PAYING} transition was observed on a source RE event in {@code [from, to)} with
   * one of {@code triggers} as application trigger, grouped by trigger and attempt dimensions. A {@code null}
   * {@code to} leaves the window open. Inferred or anomalous {@code PAYING} items are not counted.
   */
  @Transactional
  public List<ActivationGroup> activationGroups(List<String> triggers, Instant from, Instant to) {
    var query = em.createQuery(
            "SELECT t.trigger, a.serviceIdentifier, a.psp, a.canale, a.stazione, COUNT(DISTINCT a.id)"
                + " FROM AttemptTransitionEntity t JOIN t.attempt a"
                + " WHERE t.state = 'PAYING' AND t.timestampBasis = :basis AND t.trigger IN :triggers"
                + " AND t.transitionAt >= :from" + (to == null ? "" : " AND t.transitionAt < :to")
                + " GROUP BY t.trigger, a.serviceIdentifier, a.psp, a.canale, a.stazione",
            Object[].class)
        .setParameter("basis", it.gov.pagopa.nodetsworker.observability.model.TimestampBasis.SOURCE_EVENT)
        .setParameter("triggers", triggers)
        .setParameter("from", from);
    if (to != null) {
      query.setParameter("to", to);
    }
    return query.getResultList().stream()
        .map(r -> new ActivationGroup((String) r[0], blankToNull((String) r[1]), blankToNull((String) r[2]),
            blankToNull((String) r[3]), blankToNull((String) r[4]), ((Number) r[5]).longValue()))
        .toList();
  }

  /**
   * Activated attempts grouped by their accepted-outcome version (0 none, 1 V1 only, 2 any V2), current state,
   * trigger of the latest transition into the current state ({@code null} when not recorded) and dimensions.
   */
  public record TokenGroup(int outcomeVersion, String currentState, String cause, String serviceIdentifier,
      String psp, String canale, String stazione, long attempts) {
  }

  /**
   * The attempts counted by {@link #activationGroups} for the same arguments, each in exactly one group. An
   * accepted outcome is a transition triggered by {@code outcomeV1} or {@code outcomeV2}.
   */
  @Transactional
  public List<TokenGroup> tokenGroups(List<String> activationTriggers, String outcomeV1, String outcomeV2,
      Instant from, Instant to) {
    String v2 = "EXISTS (SELECT 1 FROM AttemptTransitionEntity o2 WHERE o2.attempt = a AND o2.trigger = :v2)";
    String v1 = "EXISTS (SELECT 1 FROM AttemptTransitionEntity o1 WHERE o1.attempt = a AND o1.trigger = :v1)";
    // One query per version keeps the GROUP BY free of parameterised expressions (portable across dialects).
    List<TokenGroup> result = new java.util.ArrayList<>();
    result.addAll(tokenGroups(2, v2, activationTriggers, outcomeV1, outcomeV2, from, to));
    result.addAll(tokenGroups(1, "NOT " + v2 + " AND " + v1, activationTriggers, outcomeV1, outcomeV2, from, to));
    result.addAll(tokenGroups(0, "NOT " + v2 + " AND NOT " + v1, activationTriggers, outcomeV1, outcomeV2, from,
        to));
    return result;
  }

  private List<TokenGroup> tokenGroups(int version, String versionCondition, List<String> activationTriggers,
      String outcomeV1, String outcomeV2, Instant from, Instant to) {
    var query = em.createQuery(
            "SELECT a.currentState, c.trigger, a.serviceIdentifier, a.psp, a.canale, a.stazione,"
                + " COUNT(DISTINCT a.id)"
                + " FROM AttemptTransitionEntity t JOIN t.attempt a"
                + " LEFT JOIN AttemptTransitionEntity c ON c.attempt = a AND c.state = a.currentState"
                + " AND c.id = (SELECT MAX(c2.id) FROM AttemptTransitionEntity c2 WHERE c2.attempt = a"
                + " AND c2.state = a.currentState)"
                + " WHERE t.state = 'PAYING' AND t.timestampBasis = :basis AND t.trigger IN :triggers"
                + " AND t.transitionAt >= :from" + (to == null ? "" : " AND t.transitionAt < :to")
                + " AND " + versionCondition
                + " GROUP BY a.currentState, c.trigger, a.serviceIdentifier, a.psp, a.canale, a.stazione",
            Object[].class)
        .setParameter("basis", it.gov.pagopa.nodetsworker.observability.model.TimestampBasis.SOURCE_EVENT)
        .setParameter("triggers", activationTriggers)
        .setParameter("from", from);
    if (versionCondition.contains(":v2")) {
      query.setParameter("v2", outcomeV2);
    }
    if (versionCondition.contains(":v1")) {
      query.setParameter("v1", outcomeV1);
    }
    if (to != null) {
      query.setParameter("to", to);
    }
    return query.getResultList().stream()
        .map(r -> new TokenGroup(version, (String) r[0], blankToNull((String) r[1]), blankToNull((String) r[2]),
            blankToNull((String) r[3]), blankToNull((String) r[4]), blankToNull((String) r[5]),
            ((Number) r[6]).longValue()))
        .toList();
  }

  /** Source time of the latest attempt transition observed on a source event, or {@code null}. */
  @Transactional
  public Instant lastObservedAttemptEvent() {
    return em.createQuery("SELECT MAX(t.transitionAt) FROM AttemptTransitionEntity t"
            + " WHERE t.timestampBasis = :basis", Instant.class)
        .setParameter("basis", it.gov.pagopa.nodetsworker.observability.model.TimestampBasis.SOURCE_EVENT)
        .getSingleResult();
  }

  // --- primitive counters ---------------------------------------------------

  /** Primitive counter rows whose minute lies in the window; empty-string dimensions map to null. */
  @Transactional
  public List<PrimitiveBucket> primitiveBuckets(Instant from, Instant to) {
    return em.createQuery(
            "SELECT c FROM PrimitiveCountEntity c WHERE c.bucketStart >= :from AND c.bucketStart < :to",
            PrimitiveCountEntity.class)
        .setParameter("from", PrimitiveLogic.bucketOf(from))
        .setParameter("to", to)
        .getResultList().stream()
        .map(c -> new PrimitiveBucket(c.getBucketStart(), blankToNull(c.getServiceIdentifier()),
            c.getPrimitive(), blankToNull(c.getPsp()), blankToNull(c.getCanale()), blankToNull(c.getStazione()),
            c.getRequests(), c.getResponses(), c.getWarnings(), PrimitiveDirection.parse(c.getDirection())))
        .toList();
  }

  /**
   * Calls of one primitive counted as warnings, requested between the window's first minute and {@code to}
   * (one minute is added: a call belongs to the minute of its request, which the caller checks exactly).
   */
  @Transactional
  public List<PrimitiveWarningRow> primitiveWarnings(String primitive, Instant from, Instant to) {
    return em.createQuery(
            "SELECT w FROM PrimitiveWarningEntity w WHERE w.primitive = :primitive AND w.calledAt >= :from"
                + " AND w.calledAt < :to ORDER BY w.calledAt DESC", PrimitiveWarningEntity.class)
        .setParameter("primitive", primitive)
        .setParameter("from", PrimitiveLogic.bucketOf(from))
        .setParameter("to", to.plus(1, PrimitiveLogic.BUCKET))
        .getResultList().stream()
        .map(w -> new PrimitiveWarningRow(w.getCalledAt(), blankToNull(w.getServiceIdentifier()), w.getPrimitive(),
            blankToNull(w.getPsp()), blankToNull(w.getCanale()), blankToNull(w.getPaFiscalCode()),
            blankToNull(w.getStazione())))
        .toList();
  }

  private static String blankToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  // --- outbox (consumed by the SSE publisher) -------------------------------

  @Transactional
  public List<OutboxEntity> unpublishedOutbox(int limit) {
    return em.createQuery(
            "SELECT o FROM OutboxEntity o WHERE o.published = false ORDER BY o.sequenceNo", OutboxEntity.class)
        .setMaxResults(limit)
        .getResultList();
  }

  /** Mark a drained batch as published with one bulk update per {@code pageSize} ids. */
  @Transactional
  public void markPublished(List<Long> ids, Instant publishedAt) {
    for (int start = 0; start < ids.size(); start += pageSize) {
      em.createQuery("UPDATE OutboxEntity o SET o.published = true, o.publishedAt = :at WHERE o.id IN :ids")
          .setParameter("at", publishedAt)
          .setParameter("ids", ids.subList(start, Math.min(ids.size(), start + pageSize)))
          .executeUpdate();
    }
  }

  // --- retention cleanup (spec §1, §4.4) ------------------------------------

  /**
   * Delete every attempt (and its timeline, via orphan removal) whose retention has expired, then
   * cap parent/recipient availability at the last retained attempt's expiry. Recipient-only rows use
   * their independent deadline. Each changed payment gets one revision and one outbox removal delta.
   * Returns the number of attempts removed; technical rows are also swept without deleting pending outbox rows.
   */
  @Transactional
  public int sweepExpired(Instant now, Instant outboxCutoff) {
    writer.lockProjectionMeta();
    Map<Long, PaymentEntity> changed = new LinkedHashMap<>();
    Map<Long, List<String>> removedAttempts = new LinkedHashMap<>();
    Map<Long, List<String>> removedRecipients = new LinkedHashMap<>();
    List<AttemptEntity> expired = em.createQuery(
            "SELECT a FROM AttemptEntity a WHERE a.expiresAt <= :now", AttemptEntity.class)
        .setParameter("now", now)
        .getResultList();
    for (AttemptEntity a : expired) {
      PaymentEntity payment = a.getPayment();
      changed.put(payment.getId(), payment);
      removedAttempts.computeIfAbsent(payment.getId(), ignored -> new ArrayList<>()).add(a.getAttemptId());
      payment.getAttempts().remove(a);
      em.remove(a);
    }
    // A recipient's independent deadline never extends the lifetime of a parent whose last
    // retained attempt has expired. Recipient-only observations still use their own deadline.
    for (PaymentEntity payment : changed.values()) {
      if (payment.getAttempts().isEmpty()) {
        for (RecipientEntity recipient : new ArrayList<>(payment.getRecipients())) {
          removedRecipients.computeIfAbsent(payment.getId(), ignored -> new ArrayList<>())
              .add(recipient.getRecipientId());
          payment.getRecipients().remove(recipient);
          em.remove(recipient);
        }
      }
    }
    // Recipients expire independently of attempts (their own 60-day retention anchor).
    List<RecipientEntity> expiredRecipients = em.createQuery(
            "SELECT r FROM RecipientEntity r WHERE r.expiresAt <= :now", RecipientEntity.class)
        .setParameter("now", now)
        .getResultList();
    for (RecipientEntity r : expiredRecipients) {
      PaymentEntity payment = r.getPayment();
      changed.put(payment.getId(), payment);
      removedRecipients.computeIfAbsent(payment.getId(), ignored -> new ArrayList<>()).add(r.getRecipientId());
      payment.getRecipients().remove(r);
      em.remove(r);
    }
    // Payments with no attempt and no recipient left: drop the residual logical timeline and payment.
    List<PaymentEntity> empty = em.createQuery(
            "SELECT p FROM PaymentEntity p WHERE p.attempts IS EMPTY AND p.recipients IS EMPTY",
            PaymentEntity.class)
        .getResultList();
    for (PaymentEntity p : empty) {
      changed.put(p.getId(), p);
    }
    for (PaymentEntity p : changed.values()) {
      boolean deleted = p.getAttempts().isEmpty() && p.getRecipients().isEmpty();
      writer.recordRetentionChange(p, removedAttempts.getOrDefault(p.getId(), List.of()),
          removedRecipients.getOrDefault(p.getId(), List.of()), deleted, now);
      if (deleted) {
        em.remove(p);
      }
    }
    em.createQuery("DELETE FROM InboxEntity i WHERE i.expiresAt <= :now")
        .setParameter("now", now).executeUpdate();
    em.createQuery("DELETE FROM IngestionAnomalyEntity a WHERE a.recordedAt <= :cutoff")
        .setParameter("cutoff", outboxCutoff).executeUpdate();
    em.createQuery("DELETE FROM PrimitiveCountEntity c WHERE c.bucketStart <= :cutoff")
        .setParameter("cutoff", outboxCutoff).executeUpdate();
    em.createQuery("DELETE FROM PrimitiveWarningEntity w WHERE w.calledAt <= :cutoff")
        .setParameter("cutoff", outboxCutoff).executeUpdate();
    em.createQuery("DELETE FROM OutboxEntity o WHERE o.published = true AND o.createdAt <= :cutoff")
        .setParameter("cutoff", outboxCutoff).executeUpdate();
    return expired.size();
  }
}
