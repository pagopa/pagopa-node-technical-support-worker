package it.gov.pagopa.nodetsworker.observability.repository;

import io.quarkus.hibernate.orm.PersistenceUnit;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
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
import it.gov.pagopa.nodetsworker.observability.service.ProjectionMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data access over the observability projection (second PostgreSQL datasource / persistence unit).
 * Reads assemble the in-memory domain graph inside the transaction so lazy timelines are initialized.
 */
@ApplicationScoped
public class ProjectionRepository {

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  @Inject
  IngestionProjectionWriter writer;

  /** Load the visible projection, optionally restricted to a single creditor. */
  @Transactional
  public List<DomainPayment> loadVisible(String allowedPa) {
    String jpql = "SELECT p FROM PaymentEntity p"
        + (allowedPa != null ? " WHERE p.paFiscalCode = :pa" : "");
    var query = em.createQuery(jpql, PaymentEntity.class);
    if (allowedPa != null) {
      query.setParameter("pa", allowedPa);
    }
    List<DomainPayment> out = new ArrayList<>();
    for (PaymentEntity entity : query.getResultList()) {
      out.add(ProjectionMapper.toDomain(entity));
    }
    return out;
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

  @Transactional
  public void markPublished(long id, Instant publishedAt) {
    OutboxEntity o = em.find(OutboxEntity.class, id);
    if (o != null) {
      o.setPublished(true);
      o.setPublishedAt(publishedAt);
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
