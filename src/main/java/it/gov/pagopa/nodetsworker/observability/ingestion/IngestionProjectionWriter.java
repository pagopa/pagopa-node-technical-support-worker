package it.gov.pagopa.nodetsworker.observability.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.PersistenceUnit;
import it.gov.pagopa.nodetsworker.observability.domain.DomainAttempt;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.DomainRecipient;
import it.gov.pagopa.nodetsworker.observability.dto.AttemptDto;
import it.gov.pagopa.nodetsworker.observability.dto.RecipientDto;
import it.gov.pagopa.nodetsworker.observability.ingestion.AdmissionFilter.Admitted;
import it.gov.pagopa.nodetsworker.observability.ingestion.AdmissionFilter.Entity;
import it.gov.pagopa.nodetsworker.observability.ingestion.PaymentFsm.Resolution;
import it.gov.pagopa.nodetsworker.observability.model.AttemptState;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import it.gov.pagopa.nodetsworker.observability.repository.models.AttemptEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.AttemptTransitionEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.IngestionAnomalyEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.LogicalTransitionEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.OutboxEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PaymentEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.ProjectionMetaEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.RecipientEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.RecipientTransitionEntity;
import it.gov.pagopa.nodetsworker.observability.service.ProjectionMapper;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies one admitted RE event to the observability projection and writes the matching outbox delta,
 * inside the caller's single transaction (spec §4.1/§4.2). All state resolution goes through
 * {@link PaymentFsm}; a {@code position_*} event updates only the logical payment, a {@code payment_*}
 * event only the attempt identified by {@code paymentToken}; its logical parent remains explicitly
 * incomplete until a {@code position_*} event is received.
 *
 * <p>The delta written to {@link OutboxEntity#getPaymentChanges()} is the exact {@code changes} object
 * the read-side SSE hub forwards as {@code payment.delta}:
 * {@code {logicalTransitions, attemptChanges, removedAttemptIds, deleted, timelineIncomplete?}}.
 */
@ApplicationScoped
public class IngestionProjectionWriter {

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  @Inject
  ObjectMapper objectMapper;

  /** Outcome of applying one event: whether the projection changed and whether an anomaly was recorded. */
  public record ApplyResult(boolean changed, boolean anomaly) {
  }

  private record TransitionSpec(String state, Instant instant, TimestampBasis basis, String trigger) {
    private TransitionSpec(String state, Instant instant, TimestampBasis basis) {
      this(state, instant, basis, null);
    }
  }

  /** Attempt-level outcome: the delta to publish ({@code null} = no projection change) and the anomaly flag. */
  private record AttemptResult(Map<String, Object> change, boolean anomaly) {
  }

  /** Recipient-level outcome: the delta to publish ({@code null} = no projection change) and the anomaly flag. */
  private record RecipientResult(Map<String, Object> change, boolean anomaly) {
  }

  /** Load the logical payment with a pessimistic write lock to serialize same-key updates (§4.3). */
  public PaymentEntity lockPayment(String paFiscalCode, String noticeNumber) {
    List<PaymentEntity> rows = em.createQuery(
            "SELECT p FROM PaymentEntity p WHERE p.paFiscalCode = :pa AND p.noticeNumber = :notice",
            PaymentEntity.class)
        .setParameter("pa", paFiscalCode)
        .setParameter("notice", noticeNumber)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultList();
    return rows.isEmpty() ? null : rows.get(0);
  }

  /** Acquire the shared writer lock before deduplication, so concurrent redeliveries recheck the inbox. */
  public void lockProjectionMeta() {
    lockMeta();
  }

  /**
   * Apply the admitted event. Returns {@link ApplyResult#changed()} = false when the event is a
   * duplicate / regression / unresolvable move (no projection change, no outbox).
   */
  public ApplyResult apply(Admitted admitted, ReMessage msg, EventTime.Resolved time, Instant now,
      long retentionDays) {
    String pa = msg.getIdDominio();
    String notice = msg.getNoticeNumber();
    ProjectionMetaEntity meta = lockMeta();
    PaymentEntity payment = lockPayment(pa, notice);

    Map<String, Object> changes = newChangesEnvelope();
    @SuppressWarnings("unchecked")
    List<Object> logicalTransitions = (List<Object>) changes.get("logicalTransitions");
    @SuppressWarnings("unchecked")
    List<Object> attemptChanges = (List<Object>) changes.get("attemptChanges");
    @SuppressWarnings("unchecked")
    List<Object> recipientChanges = (List<Object>) changes.get("recipientChanges");
    boolean anomaly = false;
    boolean firstPositionObservation = false;
    boolean initiallyTimelineIncomplete = payment != null && payment.isTimelineIncomplete();

    // IDENTITY ids make persist() flush the INSERT immediately, so a new payment is persisted only
    // after its accepted transition has been prepared. Attempt-only observations have no position state.
    boolean created = payment == null;
    if (admitted.entity() == Entity.POSITION) {
      String current = created ? null : payment.getCurrentState();
      firstPositionObservation = current == null;
      Resolution r = PaymentFsm.resolve(Entity.POSITION, current, admitted.state());
      if (!r.accepted()) {
        return new ApplyResult(false, false);
      }
      if (created) {
        payment = newPayment(pa, notice);
      }
      anomaly |= appendLogical(payment, r, time, admitted.trigger(), logicalTransitions);
      if (firstPositionObservation && !r.ambiguous()) {
        payment.setTimelineIncomplete(false);
      }
      if (created) {
        em.persist(payment);
      }
    } else if (admitted.entity() == Entity.PAYMENT) {
      // payment_* : update the attempt identified by paymentToken.
      if (created) {
        // Only materialize a new payment once the attempt's first transition is accepted.
        Resolution probe = PaymentFsm.resolve(Entity.PAYMENT, null, admitted.state());
        if (!probe.accepted()) {
          return new ApplyResult(false, false);
        }
        payment = newPayment(pa, notice);
        payment.setTimelineIncomplete(true);
      }
      AttemptResult ar = applyAttempt(payment, msg, admitted, time, now, retentionDays);
      anomaly |= ar.anomaly();
      if (ar.change() == null) {
        // No projection change (e.g. a value-conflict-only event): the just-built payment is simply
        // never persisted; report the anomaly without bumping revisions or writing an outbox row.
        return new ApplyResult(false, anomaly);
      }
      if (created) {
        em.persist(payment); // cascades the new attempt; no logical state is inferred from it.
      }
      attemptChanges.add(ar.change());
    } else {
      // receipt_recipient_* : update the recipient identified by stazione_recipient.
      if (created) {
        Resolution probe = PaymentFsm.resolve(Entity.RECIPIENT, null, admitted.state());
        if (!probe.accepted()) {
          return new ApplyResult(false, false);
        }
        payment = newPayment(pa, notice);
        payment.setTimelineIncomplete(true);
      }
      RecipientResult rr = applyRecipient(payment, msg, admitted, time, now, retentionDays);
      anomaly |= rr.anomaly();
      if (rr.change() == null) {
        return new ApplyResult(false, anomaly);
      }
      if (created) {
        em.persist(payment); // cascades the new recipient; no logical state is inferred from it.
      }
      recipientChanges.add(rr.change());
    }

    // A change occurred: bump revisions and publish the delta.
    payment.setProjectionRevision(payment.getProjectionRevision() + 1);
    meta.setAggregateRevision(meta.getAggregateRevision() + 1);
    meta.setLastSequence(meta.getLastSequence() + 1);
    meta.setLastProcessedAt(now);

    boolean timelineIncomplete = payment.isTimelineIncomplete()
        || payment.getAttempts().stream().anyMatch(AttemptEntity::isTimelineIncomplete)
        || payment.getRecipients().stream().anyMatch(RecipientEntity::isTimelineIncomplete);
    if (timelineIncomplete) {
      changes.put("timelineIncomplete", true);
    } else if (!created && initiallyTimelineIncomplete && firstPositionObservation) {
      changes.put("timelineIncomplete", false);
    }
    writeOutbox(meta, payment, changes, now);
    return new ApplyResult(true, anomaly);
  }

  /**
   * Reconcile a logical payment to its authoritative source state. Unlike an RE event, a source
   * snapshot is not constrained by the FSM: its state is appended directly with sync provenance.
   *
   * @return {@code true} when a transition was appended, {@code false} for unsupported or matching
   *     source states
   */
  @Transactional
  public boolean reconcileLogical(String paFiscalCode, String noticeNumber, String sourceState,
      Instant sourceInstant) {
    if (sourceState == null || !PaymentFsm.isKnownState(Entity.POSITION, sourceState)) {
      return false;
    }
    ProjectionMetaEntity meta = lockMeta();
    PaymentEntity payment = lockPayment(paFiscalCode, noticeNumber);
    if (payment != null && sourceState.equals(payment.getCurrentState())) {
      return false;
    }
    boolean created = payment == null;
    if (created) {
      payment = newPayment(paFiscalCode, noticeNumber);
    }

    Map<String, Object> changes = newChangesEnvelope();
    @SuppressWarnings("unchecked")
    List<Object> logicalTransitions = (List<Object>) changes.get("logicalTransitions");
    appendLogicalItem(payment,
        new TransitionSpec(sourceState, sourceInstant, TimestampBasis.SOURCE_SYNC), logicalTransitions);
    if (created) {
      em.persist(payment);
    }
    recordSyncChange(meta, payment, changes, Instant.now());
    return true;
  }

  /**
   * Reconcile one token-scoped attempt to its authoritative source state. If the logical parent is
   * missing, its state comes only from a supplied logical snapshot. Otherwise the parent remains
   * explicitly incomplete with no logical state, in the same outbox delta as the attempt.
   *
   * @return {@code true} when any projection state changed, {@code false} for unsupported or matching
   *     source states
   */
  @Transactional
  public boolean reconcileAttempt(String paFiscalCode, String noticeNumber, String paymentToken,
      String sourceState, Instant sourceInstant, String logicalSourceState, Instant logicalSourceInstant,
      long retentionDays) {
    if (paymentToken == null || sourceState == null || !PaymentFsm.isKnownState(Entity.PAYMENT, sourceState)
        || (logicalSourceState != null && !PaymentFsm.isKnownState(Entity.POSITION, logicalSourceState))) {
      return false;
    }
    ProjectionMetaEntity meta = lockMeta();
    PaymentEntity payment = lockPayment(paFiscalCode, noticeNumber);
    AttemptEntity attempt = payment == null ? null : findAttempt(payment, paymentToken);
    if (attempt != null && sourceState.equals(attempt.getCurrentState())) {
      return false;
    }

    boolean createdPayment = payment == null;
    if (createdPayment) {
      payment = newPayment(paFiscalCode, noticeNumber);
    }
    Map<String, Object> changes = newChangesEnvelope();
    @SuppressWarnings("unchecked")
    List<Object> logicalTransitions = (List<Object>) changes.get("logicalTransitions");
    @SuppressWarnings("unchecked")
    List<Object> attemptChanges = (List<Object>) changes.get("attemptChanges");

    if (payment.getTimeline().isEmpty() || payment.getCurrentState() == null) {
      if (logicalSourceState != null) {
        appendLogicalItem(payment,
            new TransitionSpec(logicalSourceState, logicalSourceInstant, TimestampBasis.SOURCE_SYNC),
            logicalTransitions);
      } else {
        payment.setTimelineIncomplete(true);
      }
    }

    Instant now = Instant.now();
    boolean createdAttempt = attempt == null;
    if (createdAttempt) {
      boolean startsOnPaying = "PAYING".equals(sourceState);
      TimestampBasis startBasis = startsOnPaying ? TimestampBasis.SOURCE_SYNC : TimestampBasis.INFERRED;
      Instant startAt = sourceInstant;
      Instant retentionAnchor = startAt != null ? startAt : now;
      attempt = AttemptEntity.builder()
          .payment(payment)
          .attemptId(attemptId(paymentToken))
          .paymentToken(paymentToken)
          .attemptStartedAt(startAt)
          .startTimestampBasis(startBasis)
          .firstSeenAt(now)
          .expiresAt(retentionAnchor.plus(retentionDays, ChronoUnit.DAYS))
          .timelineIncomplete(false)
          .currentState(null)
          .timeline(new ArrayList<>())
          .build();
      payment.getAttempts().add(attempt);
    }

    TransitionSpec sourceTransition =
        new TransitionSpec(sourceState, sourceInstant, TimestampBasis.SOURCE_SYNC);
    appendAttemptItem(attempt, sourceTransition);
    Map<String, Object> attemptChange = new LinkedHashMap<>();
    List<Object> transitions = new ArrayList<>();
    transitions.add(transitionMap(sourceTransition));
    attemptChange.put("transitions", transitions);
    attemptChange.put("attemptId", attempt.getAttemptId());
    attemptChange.put("warning", AttemptState.WARNING.contains(sourceState));
    if (createdAttempt) {
      attemptChange.put("transitions", List.of());
      attemptChange.put("upsert", safeAttemptUpsert(payment, attempt));
    }
    if (attempt.isTimelineIncomplete()) {
      attemptChange.put("patch", Map.of("timelineIncomplete", true));
    }
    attemptChanges.add(attemptChange);

    if (createdPayment) {
      em.persist(payment);
    }
    recordSyncChange(meta, payment, changes, now);
    return true;
  }

  private void recordSyncChange(ProjectionMetaEntity meta, PaymentEntity payment,
      Map<String, Object> changes, Instant now) {
    payment.setProjectionRevision(payment.getProjectionRevision() + 1);
    meta.setAggregateRevision(meta.getAggregateRevision() + 1);
    meta.setLastSequence(meta.getLastSequence() + 1);
    meta.setLastProcessedAt(now);
    if (payment.isTimelineIncomplete()
        || payment.getAttempts().stream().anyMatch(AttemptEntity::isTimelineIncomplete)) {
      changes.put("timelineIncomplete", true);
    }
    writeOutbox(meta, payment, changes, now);
  }

  /** Publish retention removals in the same transaction as the deleted projection records. */
  public void recordRetentionChange(PaymentEntity payment, List<String> attemptIds,
      List<String> recipientIds, boolean deleted, Instant now) {
    Map<String, Object> changes = newChangesEnvelope();
    changes.put("removedAttemptIds", attemptIds);
    changes.put("removedRecipientIds", recipientIds);
    changes.put("deleted", deleted);
    recordSyncChange(lockMeta(), payment, changes, now);
  }

  // --- logical payment ------------------------------------------------------

  /** Build a new parent row; its logical state remains absent until a position event arrives. */
  private PaymentEntity newPayment(String pa, String notice) {
    return PaymentEntity.builder()
        .paFiscalCode(pa).noticeNumber(notice)
        .projectionRevision(0).timelineIncomplete(false)
        .currentState(null)
        .timeline(new ArrayList<>()).attempts(new ArrayList<>())
        .build();
  }

  private boolean appendLogical(PaymentEntity payment, Resolution r, EventTime.Resolved time, String trigger,
      List<Object> delta) {
    for (TransitionSpec spec : specs(r, time, trigger)) {
      appendLogicalItem(payment, spec, delta);
    }
    if (r.ambiguous()) {
      payment.setTimelineIncomplete(true);
      return true;
    }
    return false;
  }

  private void appendLogicalItem(PaymentEntity payment, TransitionSpec spec, List<Object> delta) {
    LogicalTransitionEntity t = LogicalTransitionEntity.builder()
        .payment(payment).state(spec.state()).transitionAt(spec.instant()).timestampBasis(spec.basis())
        .trigger(spec.trigger())
        .build();
    payment.getTimeline().add(t);
    payment.setCurrentState(spec.state());
    delta.add(transitionMap(spec));
  }

  // --- attempt --------------------------------------------------------------

  private AttemptResult applyAttempt(PaymentEntity payment, ReMessage msg, Admitted admitted,
      EventTime.Resolved time, Instant now, long retentionDays) {
    AttemptEntity attempt = findAttempt(payment, msg.getPaymentToken());
    Map<String, Object> change = new LinkedHashMap<>();
    boolean changed = false;
    boolean anomaly = false;
    boolean created = false;

    if (attempt == null) {
      Resolution r = PaymentFsm.resolve(Entity.PAYMENT, null, admitted.state());
      if (!r.accepted()) {
        return new AttemptResult(null, false);
      }
      attempt = createAttempt(payment, msg, admitted, time, now, retentionDays, r, change);
      changed = true;
      created = true;
      anomaly |= r.ambiguous();
    } else {
      List<TransitionSpec> appended = new ArrayList<>();
      Resolution r = PaymentFsm.resolve(Entity.PAYMENT, attempt.getCurrentState(), admitted.state());
      if (r.accepted()) {
        for (TransitionSpec spec : specs(r, time, admitted.trigger())) {
          appendAttemptItem(attempt, spec);
          appended.add(spec);
        }
        if (r.ambiguous()) {
          attempt.setTimelineIncomplete(true);
          anomaly = true;
        }
        changed = true;
      } else if ("PAYING".equals(admitted.state())
          && attempt.getStartTimestampBasis() == TimestampBasis.INFERRED && time.instant() != null) {
        // Late payment_PAYING: does not change state, but replaces the estimated start (§4.3).
        attempt.setAttemptStartedAt(time.instant());
        attempt.setStartTimestampBasis(TimestampBasis.SOURCE_EVENT);
        Instant correctedExpiry = time.instant().plus(retentionDays, ChronoUnit.DAYS);
        if (attempt.getExpiresAt() == null || correctedExpiry.isBefore(attempt.getExpiresAt())) {
          attempt.setExpiresAt(correctedExpiry);
        }
        int correctedIndex = correctFirstInferredPaying(attempt, time.instant(), admitted.trigger());
        if (correctedIndex >= 0) {
          Map<String, Object> correction = new LinkedHashMap<>();
          correction.put("index", correctedIndex);
          correction.put("transition", transitionMap(
              new TransitionSpec("PAYING", time.instant(), TimestampBasis.SOURCE_EVENT, admitted.trigger())));
          change.put("transitionCorrections", List.of(correction));
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("attemptStartedAt", IsoTime.format(time.instant()));
        patch.put("startTimestampBasis", TimestampBasis.SOURCE_EVENT.name());
        change.put("patch", patch);
        changed = true;
      }
      if (!appended.isEmpty()) {
        List<Object> transitions = new ArrayList<>();
        for (TransitionSpec spec : appended) {
          transitions.add(transitionMap(spec));
        }
        change.put("transitions", transitions);
      }
      anomaly |= acquireFields(attempt, msg, change);
      // attemptId/warning are added below; any entry here means a real change (transition, start
      // correction or a newly acquired dimension).
      changed |= !change.isEmpty();
    }

    if (!changed) {
      return new AttemptResult(null, anomaly);
    }
    change.put("attemptId", attempt.getAttemptId());
    change.put("warning", AttemptState.WARNING.contains(attempt.getCurrentState()));
    if (created) {
      change.put("transitions", List.of());
      change.remove("patch");
      change.put("upsert", safeAttemptUpsert(payment, attempt));
    } else if (attempt.isTimelineIncomplete()) {
      patch(change).put("timelineIncomplete", true);
    }
    return new AttemptResult(change, anomaly);
  }

  private AttemptEntity createAttempt(PaymentEntity payment, ReMessage msg, Admitted admitted,
      EventTime.Resolved time, Instant now, long retentionDays, Resolution r, Map<String, Object> change) {
    boolean startedOnPaying = "PAYING".equals(admitted.state());
    Instant startedAt;
    TimestampBasis startBasis;
    if (time.instant() == null) {
      startedAt = null;
      startBasis = TimestampBasis.ANOMALY;
    } else if (startedOnPaying) {
      startedAt = time.instant();
      startBasis = TimestampBasis.SOURCE_EVENT;
    } else {
      // A later state was observed first: the start is estimated (upper bound) until a PAYING arrives.
      startedAt = time.instant();
      startBasis = TimestampBasis.INFERRED;
    }
    Instant retentionAnchor = startedAt != null ? startedAt : now;
    AttemptEntity attempt = AttemptEntity.builder()
        .payment(payment)
        .attemptId(attemptId(msg.getPaymentToken()))
        .paymentToken(msg.getPaymentToken())
        .serviceIdentifier(msg.getServiceIdentifier())
        .psp(msg.getPsp()).stazione(msg.getStazione()).canale(msg.getCanale())
        .attemptStartedAt(startedAt).startTimestampBasis(startBasis)
        .firstSeenAt(now).expiresAt(retentionAnchor.plus(retentionDays, ChronoUnit.DAYS))
        .timelineIncomplete(r.ambiguous())
        .currentState(null)
        .timeline(new ArrayList<>())
        .build();
    payment.getAttempts().add(attempt);

    List<Object> transitions = new ArrayList<>();
    for (TransitionSpec spec : specs(r, time, admitted.trigger())) {
      appendAttemptItem(attempt, spec);
      transitions.add(transitionMap(spec));
    }
    change.put("transitions", transitions);
    Map<String, Object> patch = new LinkedHashMap<>();
    putIfPresent(patch, "serviceIdentifier", msg.getServiceIdentifier());
    putIfPresent(patch, "psp", msg.getPsp());
    putIfPresent(patch, "stazione", msg.getStazione());
    putIfPresent(patch, "canale", msg.getCanale());
    patch.put("attemptStartedAt", IsoTime.format(startedAt));
    patch.put("startTimestampBasis", startBasis.name());
    if (!patch.isEmpty()) {
      change.put("patch", patch);
    }
    return attempt;
  }

  /** Acquire still-missing {@code serviceIdentifier}/{@code psp}/{@code stazione}/{@code canale}; a
   * differing non-null value is a technical anomaly and never overwrites (§4.1). */
  private boolean acquireFields(AttemptEntity attempt, ReMessage msg, Map<String, Object> change) {
    boolean anomaly = false;
    Map<String, Object> patch = new LinkedHashMap<>();
    anomaly |= acquire(patch, "serviceIdentifier", attempt.getServiceIdentifier(), msg.getServiceIdentifier(),
        attempt::setServiceIdentifier);
    anomaly |= acquire(patch, "psp", attempt.getPsp(), msg.getPsp(), attempt::setPsp);
    anomaly |= acquire(patch, "stazione", attempt.getStazione(), msg.getStazione(), attempt::setStazione);
    anomaly |= acquire(patch, "canale", attempt.getCanale(), msg.getCanale(), attempt::setCanale);
    if (!patch.isEmpty()) {
      patch(change).putAll(patch);
    }
    return anomaly;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> patch(Map<String, Object> change) {
    return (Map<String, Object>) change.computeIfAbsent("patch", ignored -> new LinkedHashMap<>());
  }

  private AttemptDto safeAttemptUpsert(PaymentEntity payment, AttemptEntity attempt) {
    DomainPayment domainPayment = ProjectionMapper.toDomain(payment);
    domainPayment.setProjectionRevision(payment.getProjectionRevision() + 1);
    DomainAttempt domainAttempt = domainPayment.getAttempts().stream()
        .filter(candidate -> attempt.getAttemptId().equals(candidate.getAttemptId()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("New attempt missing from its payment"));
    return ProjectionMapper.toSafeAttemptDto(domainAttempt, domainPayment);
  }

  private boolean acquire(Map<String, Object> change, String field, String current, String incoming,
      java.util.function.Consumer<String> setter) {
    if (incoming == null) {
      return false;
    }
    if (current == null) {
      setter.accept(incoming);
      change.put(field, incoming);
      return false;
    }
    // Non-null and different: record as a technical anomaly, keep the acquired value.
    return !current.equals(incoming);
  }

  private void appendAttemptItem(AttemptEntity attempt, TransitionSpec spec) {
    AttemptTransitionEntity t = AttemptTransitionEntity.builder()
        .attempt(attempt).state(spec.state()).transitionAt(spec.instant()).timestampBasis(spec.basis())
        .trigger(spec.trigger())
        .build();
    attempt.getTimeline().add(t);
    attempt.setCurrentState(spec.state());
  }

  private int correctFirstInferredPaying(AttemptEntity attempt, Instant instant, String trigger) {
    for (int index = 0; index < attempt.getTimeline().size(); index++) {
      AttemptTransitionEntity t = attempt.getTimeline().get(index);
      if ("PAYING".equals(t.getState()) && t.getTimestampBasis() != TimestampBasis.SOURCE_EVENT) {
        t.setTransitionAt(instant);
        t.setTimestampBasis(TimestampBasis.SOURCE_EVENT);
        t.setTrigger(trigger);
        return index;
      }
    }
    return -1;
  }

  private AttemptEntity findAttempt(PaymentEntity payment, String paymentToken) {
    if (paymentToken == null) {
      return null;
    }
    for (AttemptEntity a : payment.getAttempts()) {
      if (paymentToken.equals(a.getPaymentToken())) {
        return a;
      }
    }
    return null;
  }

  // --- recipient ------------------------------------------------------------

  private RecipientResult applyRecipient(PaymentEntity payment, ReMessage msg, Admitted admitted,
      EventTime.Resolved time, Instant now, long retentionDays) {
    String station = msg.recipientStation();
    RecipientEntity recipient = findRecipient(payment, station);
    Map<String, Object> change = new LinkedHashMap<>();
    boolean changed = false;
    boolean anomaly = false;
    boolean created = false;

    if (recipient == null) {
      Resolution r = PaymentFsm.resolve(Entity.RECIPIENT, null, admitted.state());
      if (!r.accepted()) {
        return new RecipientResult(null, false);
      }
      recipient = createRecipient(payment, station, admitted, time, now, retentionDays, r, change);
      changed = true;
      created = true;
      anomaly |= r.ambiguous();
    } else {
      List<TransitionSpec> appended = new ArrayList<>();
      Resolution r = PaymentFsm.resolve(Entity.RECIPIENT, recipient.getCurrentState(), admitted.state());
      if (r.accepted()) {
        for (TransitionSpec spec : specs(r, time, admitted.trigger())) {
          appendRecipientItem(recipient, spec);
          appended.add(spec);
        }
        if (r.ambiguous()) {
          recipient.setTimelineIncomplete(true);
          anomaly = true;
        }
        changed = true;
      } else if (time.instant() != null
          && correctInferredRecipient(recipient, admitted.state(), time, admitted.trigger())) {
        // Replace only a uniquely identifiable inferred occurrence, never append a backward move.
        change.put("transitions", List.of());
        change.put("upsert", safeRecipientUpsert(payment, recipient));
        changed = true;
      }
      if (changed && time.instant() != null) {
        Instant deadline = time.instant().plus(retentionDays, ChronoUnit.DAYS);
        if (recipient.getExpiresAt() == null || deadline.isBefore(recipient.getExpiresAt())) {
          recipient.setExpiresAt(deadline);
        }
      }
      if (!appended.isEmpty()) {
        List<Object> transitions = new ArrayList<>();
        for (TransitionSpec spec : appended) {
          transitions.add(transitionMap(spec));
        }
        change.put("transitions", transitions);
      }
      changed |= !change.isEmpty();
    }

    if (!changed) {
      return new RecipientResult(null, anomaly);
    }
    change.put("recipientId", recipient.getRecipientId());
    if (created) {
      change.put("transitions", List.of());
      change.put("upsert", safeRecipientUpsert(payment, recipient));
    } else if (recipient.isTimelineIncomplete()) {
      patch(change).put("timelineIncomplete", true);
    }
    return new RecipientResult(change, anomaly);
  }

  private RecipientEntity createRecipient(PaymentEntity payment, String station, Admitted admitted,
      EventTime.Resolved time, Instant now, long retentionDays, Resolution r, Map<String, Object> change) {
    Instant retentionAnchor = time.instant() != null ? time.instant() : now;
    RecipientEntity recipient = RecipientEntity.builder()
        .payment(payment)
        .recipientId(recipientId(payment.getPaFiscalCode(), payment.getNoticeNumber(), station))
        .stazioneRecipient(station)
        .firstSeenAt(now)
        .expiresAt(retentionAnchor.plus(retentionDays, ChronoUnit.DAYS))
        .timelineIncomplete(r.ambiguous())
        .currentState(null)
        .timeline(new ArrayList<>())
        .build();
    payment.getRecipients().add(recipient);

    List<Object> transitions = new ArrayList<>();
    for (TransitionSpec spec : specs(r, time, admitted.trigger())) {
      appendRecipientItem(recipient, spec);
      transitions.add(transitionMap(spec));
    }
    change.put("transitions", transitions);
    return recipient;
  }

  private void appendRecipientItem(RecipientEntity recipient, TransitionSpec spec) {
    RecipientTransitionEntity t = RecipientTransitionEntity.builder()
        .recipient(recipient).state(spec.state()).transitionAt(spec.instant()).timestampBasis(spec.basis())
        .trigger(spec.trigger())
        .build();
    recipient.getTimeline().add(t);
    recipient.setCurrentState(spec.state());
  }

  private boolean correctInferredRecipient(RecipientEntity recipient, String state,
      EventTime.Resolved time, String trigger) {
    RecipientTransitionEntity inferred = null;
    for (RecipientTransitionEntity transition : recipient.getTimeline()) {
      if (state.equals(transition.getState())) {
        if (inferred != null || transition.getTimestampBasis() != TimestampBasis.INFERRED) {
          return false;
        }
        inferred = transition;
      }
    }
    if (inferred == null) {
      return false;
    }
    inferred.setTransitionAt(time.instant());
    inferred.setTimestampBasis(time.basis());
    inferred.setTrigger(trigger);
    return true;
  }

  private RecipientEntity findRecipient(PaymentEntity payment, String station) {
    for (RecipientEntity r : payment.getRecipients()) {
      if (Objects.equals(station, r.getStazioneRecipient())) {
        return r;
      }
    }
    return null;
  }

  private RecipientDto safeRecipientUpsert(PaymentEntity payment, RecipientEntity recipient) {
    DomainPayment domainPayment = ProjectionMapper.toDomain(payment);
    domainPayment.setProjectionRevision(payment.getProjectionRevision() + 1);
    DomainRecipient domainRecipient = domainPayment.getRecipients().stream()
        .filter(candidate -> recipient.getRecipientId().equals(candidate.getRecipientId()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("New recipient missing from its payment"));
    return ProjectionMapper.toRecipientDto(domainRecipient, domainPayment);
  }

  // --- transition specs / delta ---------------------------------------------

  /** Last state of the path is the real event ({@code SOURCE_EVENT}/{@code ANOMALY}); earlier ones are inferred. */
  private List<TransitionSpec> specs(Resolution r, EventTime.Resolved time, String trigger) {
    List<String> path = r.path();
    List<TransitionSpec> specs = new ArrayList<>(path.size());
    for (int i = 0; i < path.size(); i++) {
      boolean last = i == path.size() - 1;
      if (last) {
        specs.add(new TransitionSpec(path.get(i), time.instant(), time.basis(), trigger));
      } else if (time.instant() != null) {
        specs.add(new TransitionSpec(path.get(i), time.instant(), TimestampBasis.INFERRED, trigger));
      } else {
        specs.add(new TransitionSpec(path.get(i), null, TimestampBasis.ANOMALY, trigger));
      }
    }
    return specs;
  }

  private Map<String, Object> transitionMap(TransitionSpec spec) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("state", spec.state());
    m.put("transitionAt", IsoTime.format(spec.instant()));
    m.put("timestampBasis", spec.basis().name());
    m.put("trigger", spec.trigger());
    return m;
  }

  private Map<String, Object> newChangesEnvelope() {
    Map<String, Object> changes = new LinkedHashMap<>();
    changes.put("logicalTransitions", new ArrayList<>());
    changes.put("attemptChanges", new ArrayList<>());
    changes.put("removedAttemptIds", new ArrayList<>());
    changes.put("recipientChanges", new ArrayList<>());
    changes.put("removedRecipientIds", new ArrayList<>());
    changes.put("deleted", false);
    return changes;
  }

  /**
   * Publish primitive counter changes as one aggregate-only outbox row (no payment key): bumps the
   * aggregate revision so SSE clients apply the signed differences in order.
   */
  public void recordPrimitiveChanges(java.util.List<it.gov.pagopa.nodetsworker.observability.domain.PrimitiveBucket> changes,
      Instant now) {
    if (changes.isEmpty()) {
      return;
    }
    ProjectionMetaEntity meta = lockMeta();
    meta.setAggregateRevision(meta.getAggregateRevision() + 1);
    meta.setLastSequence(meta.getLastSequence() + 1);
    meta.setLastProcessedAt(now);
    String json;
    try {
      json = objectMapper.writeValueAsString(
          it.gov.pagopa.nodetsworker.observability.domain.PrimitiveBucket.toMaps(changes));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot serialize primitive changes", e);
    }
    em.persist(OutboxEntity.builder()
        .sequenceNo(meta.getLastSequence())
        .aggregateRevision(meta.getAggregateRevision())
        .primitiveChanges(json)
        .suppressPayment(false)
        .createdAt(now)
        .published(false)
        .build());
  }

  private void writeOutbox(ProjectionMetaEntity meta, PaymentEntity payment, Map<String, Object> changes,
      Instant now) {
    String json;
    try {
      json = objectMapper.writeValueAsString(changes);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot serialize payment delta", e);
    }
    OutboxEntity row = OutboxEntity.builder()
        .sequenceNo(meta.getLastSequence())
        .aggregateRevision(meta.getAggregateRevision())
        .paFiscalCode(payment.getPaFiscalCode())
        .noticeNumber(payment.getNoticeNumber())
        .paymentRevision(payment.getProjectionRevision())
        .paymentChanges(json)
        .suppressPayment(false)
        .createdAt(now)
        .published(false)
        .build();
    em.persist(row);
  }

  // --- technical metadata ---------------------------------------------------

  private ProjectionMetaEntity lockMeta() {
    ProjectionMetaEntity meta = em.find(ProjectionMetaEntity.class, 1, LockModeType.PESSIMISTIC_WRITE);
    if (meta == null) {
      meta = ProjectionMetaEntity.builder()
          .id(1).aggregateRevision(1).lastSequence(0)
          .freshnessStatus("CURRENT").freshnessLagSeconds(0).freshnessGapsDetected(false)
          .lastProcessedAt(Instant.now()).lastPublishedAt(Instant.now())
          .build();
      em.persist(meta);
      em.flush();
    }
    return meta;
  }

  public void recordAnomaly(IngestionAnomalyEntity anomaly) {
    em.persist(anomaly);
  }

  // --- helpers --------------------------------------------------------------

  private static void putIfPresent(Map<String, Object> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  /** Stable, token-derived attempt id exposed by the functional API (never the raw token). */
  static String attemptId(String paymentToken) {
    if (paymentToken == null) {
      return "att_unknown";
    }
    return shortId("att_", paymentToken);
  }

  /** Stable recipient id derived from the logical pair and the {@code stazione_recipient} discriminator. */
  static String recipientId(String paFiscalCode, String noticeNumber, String station) {
    return shortId("rcp_", paFiscalCode + "|" + noticeNumber + "|" + (station == null ? "" : station));
  }

  private static String shortId(String prefix, String material) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(material.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(prefix);
      for (int i = 0; i < 8; i++) {
        hex.append(String.format("%02x", digest[i]));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
