package it.gov.pagopa.nodetsworker.observability.ingestion;

import io.quarkus.hibernate.orm.PersistenceUnit;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveBucket;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveDirection;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveLogic;
import it.gov.pagopa.nodetsworker.observability.ingestion.PrimitiveAdmission.Admitted;
import it.gov.pagopa.nodetsworker.observability.ingestion.PrimitiveAdmission.Kind;
import it.gov.pagopa.nodetsworker.observability.repository.models.PrimitiveCallEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PrimitiveCountEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PrimitiveWarningEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Counts primitive calls from the RE interface events (spec §3.3.2). A REQ and a RESP are paired by
 * ({@code sessionId}, {@code tipoEvento}) in the transient {@code primitive_call} table; only per-minute
 * counters survive in {@code primitive_count}. A call is a <b>warning</b>, exactly once, when its
 * response arrives more than the budget after the request, or when no response has arrived once the
 * budget has elapsed. The warning is attributed to the minute of the request, like the call itself,
 * and its request attributes are kept in {@code primitive_warning} for the detail list.
 *
 * <p>All writes run in the caller's transaction on the {@code observability} persistence unit and are
 * published as one aggregate-only outbox row, serialized by the projection metadata lock.
 */
@ApplicationScoped
public class PrimitiveIngestionService {

  private static final int DIMENSION_MAX = 64;

  @Inject
  @PersistenceUnit("observability")
  EntityManager em;

  @Inject
  IngestionProjectionWriter writer;

  @ConfigProperty(name = "observability.primitives.response-budget-seconds", defaultValue = "10")
  long responseBudgetSeconds;

  @ConfigProperty(name = "observability.primitives.pending-ttl-seconds", defaultValue = "300")
  long pendingTtlSeconds;

  /** Dimension combination plus minute, the key of one counter row. */
  private record Key(Instant bucket, String service, String primitive, String direction, String psp,
      String canale, String stazione) {
  }

  /**
   * Apply one admitted REQ/RESP at event time {@code at}. Returns {@code false} for a duplicate leg
   * (no change, nothing published).
   */
  public boolean apply(Admitted admitted, ReMessage msg, Instant at, Instant now) {
    PrimitiveCallEntity call = find(admitted.sessionId(), admitted.tipoEvento());
    Map<Key, long[]> deltas = new LinkedHashMap<>();
    if (admitted.kind() == Kind.REQUEST) {
      if (call != null && call.getRequestAt() != null) {
        return false;
      }
      if (call == null) {
        call = newCall(admitted, msg, now);
      }
      call.setRequestAt(at);
      call.setServiceIdentifier(dimension(msg.getServiceIdentifier()));
      call.setDirection(directionOf(msg));
      call.setPsp(dimension(msg.getPsp()));
      call.setCanale(dimension(msg.getCanale()));
      call.setStazione(dimension(msg.getStazione()));
      call.setPaFiscalCode(dimension(msg.getIdDominio()));
      add(deltas, call, 1, 0, 0);
      if (call.getResponseAt() != null) {
        settle(call, deltas);
      }
    } else {
      if (call != null && call.getResponseAt() != null) {
        return false;
      }
      if (call == null) {
        call = newCall(admitted, msg, now);
      }
      call.setResponseAt(at);
      if (call.getRequestAt() != null) {
        settle(call, deltas);
      }
    }
    flush(deltas, now);
    return true;
  }

  /**
   * Flag the calls whose response is overdue and drop stale work rows. A request is overdue when its
   * request time and the time it was ingested are both older than the budget, so a request replayed
   * late still gets the budget to see its response.
   *
   * @return the number of calls newly flagged as warnings
   */
  @Transactional
  public int sweep(Instant now) {
    writer.lockProjectionMeta();
    Instant cutoff = now.minusSeconds(responseBudgetSeconds);
    List<PrimitiveCallEntity> overdue = em.createQuery(
            "SELECT c FROM PrimitiveCallEntity c WHERE c.warned = false AND c.requestAt IS NOT NULL"
                + " AND c.responseAt IS NULL AND c.requestAt <= :cutoff AND c.createdAt <= :cutoff",
            PrimitiveCallEntity.class)
        .setParameter("cutoff", cutoff)
        .getResultList();
    Map<Key, long[]> deltas = new LinkedHashMap<>();
    for (PrimitiveCallEntity call : overdue) {
      call.setWarned(true);
      recordWarning(call);
      add(deltas, call, 0, 0, 1);
    }
    flush(deltas, now);
    em.createQuery("DELETE FROM PrimitiveCallEntity c WHERE c.createdAt <= :stale")
        .setParameter("stale", now.minus(Duration.ofSeconds(pendingTtlSeconds)))
        .executeUpdate();
    return overdue.size();
  }

  private void settle(PrimitiveCallEntity call, Map<Key, long[]> deltas) {
    long latencyMs = Duration.between(call.getRequestAt(), call.getResponseAt()).toMillis();
    boolean late = !call.isWarned() && latencyMs > responseBudgetSeconds * 1000L;
    add(deltas, call, 0, 1, late ? 1 : 0);
    if (late) {
      recordWarning(call);
    }
    em.remove(call);
  }

  /** Keep the request attributes of a call counted as a warning, for the detail list (spec §3.3.2). */
  private void recordWarning(PrimitiveCallEntity call) {
    em.persist(PrimitiveWarningEntity.builder()
        .calledAt(call.getRequestAt())
        .serviceIdentifier(call.getServiceIdentifier())
        .primitive(call.getTipoEvento())
        .psp(call.getPsp())
        .canale(call.getCanale())
        .paFiscalCode(call.getPaFiscalCode())
        .stazione(call.getStazione())
        .build());
  }

  private PrimitiveCallEntity newCall(Admitted admitted, ReMessage msg, Instant now) {
    PrimitiveCallEntity call = PrimitiveCallEntity.builder()
        .sessionId(admitted.sessionId())
        .tipoEvento(admitted.tipoEvento())
        .serviceIdentifier(dimension(msg.getServiceIdentifier()))
        .direction(directionOf(msg))
        .psp(dimension(msg.getPsp()))
        .canale(dimension(msg.getCanale()))
        .stazione(dimension(msg.getStazione()))
        .paFiscalCode(dimension(msg.getIdDominio()))
        .warned(false)
        .createdAt(now)
        .build();
    em.persist(call);
    return call;
  }

  private PrimitiveCallEntity find(String sessionId, String tipoEvento) {
    List<PrimitiveCallEntity> rows = em.createQuery(
            "SELECT c FROM PrimitiveCallEntity c WHERE c.sessionId = :s AND c.tipoEvento = :t",
            PrimitiveCallEntity.class)
        .setParameter("s", sessionId)
        .setParameter("t", tipoEvento)
        .getResultList();
    return rows.isEmpty() ? null : rows.get(0);
  }

  private static void add(Map<Key, long[]> deltas, PrimitiveCallEntity call, long requests, long responses,
      long warnings) {
    Key key = new Key(PrimitiveLogic.bucketOf(call.getRequestAt()), call.getServiceIdentifier(),
        call.getTipoEvento(), call.getDirection(), call.getPsp(), call.getCanale(), call.getStazione());
    long[] sum = deltas.computeIfAbsent(key, k -> new long[3]);
    sum[0] += requests;
    sum[1] += responses;
    sum[2] += warnings;
  }

  /** Add the deltas to the counter rows and publish them as one outbox row. */
  private void flush(Map<Key, long[]> deltas, Instant now) {
    List<PrimitiveBucket> changes = new ArrayList<>();
    for (Map.Entry<Key, long[]> e : deltas.entrySet()) {
      Key k = e.getKey();
      long[] d = e.getValue();
      if (d[0] == 0 && d[1] == 0 && d[2] == 0) {
        continue;
      }
      List<PrimitiveCountEntity> rows = em.createQuery(
              "SELECT c FROM PrimitiveCountEntity c WHERE c.bucketStart = :b AND c.serviceIdentifier = :s"
                  + " AND c.primitive = :r AND c.direction = :d AND c.psp = :p AND c.canale = :c AND c.stazione = :z", PrimitiveCountEntity.class)
          .setParameter("b", k.bucket()).setParameter("s", k.service()).setParameter("r", k.primitive()).setParameter("d", k.direction()).setParameter("p", k.psp())
          .setParameter("c", k.canale()).setParameter("z", k.stazione())
          .getResultList();
      PrimitiveCountEntity row = rows.isEmpty()
          ? PrimitiveCountEntity.builder().bucketStart(k.bucket()).serviceIdentifier(k.service())
              .primitive(k.primitive()).direction(k.direction()).psp(k.psp()).canale(k.canale()).stazione(k.stazione()).build()
          : rows.get(0);
      row.setRequests(row.getRequests() + d[0]);
      row.setResponses(row.getResponses() + d[1]);
      row.setWarnings(row.getWarnings() + d[2]);
      if (rows.isEmpty()) {
        em.persist(row);
      }
      changes.add(new PrimitiveBucket(k.bucket(), nullable(k.service()), k.primitive(), nullable(k.psp()),
          nullable(k.canale()), nullable(k.stazione()), d[0], d[1], d[2], PrimitiveDirection.parse(k.direction())));
    }
    writer.recordPrimitiveChanges(changes, now);
  }

  /** Direction from the RE parties of the event; a request, which is authoritative, replaces one set by a response. */
  private static String directionOf(ReMessage msg) {
    return PrimitiveDirection.of(msg.getFruitore(), msg.getErogatore()).name();
  }

  /** Empty string is the stored form of "not available". */
  private static String dimension(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.length() > DIMENSION_MAX ? trimmed.substring(0, DIMENSION_MAX) : trimmed;
  }

  private static String nullable(String stored) {
    return stored.isEmpty() ? null : stored;
  }
}
