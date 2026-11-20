package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.domain.DomainAttempt;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.DomainRecipient;
import it.gov.pagopa.nodetsworker.observability.domain.ProjectionLogic;
import it.gov.pagopa.nodetsworker.observability.domain.TimelineItem;
import it.gov.pagopa.nodetsworker.observability.dto.AggregateDto;
import it.gov.pagopa.nodetsworker.observability.dto.AnomaliesDto;
import it.gov.pagopa.nodetsworker.observability.dto.CountsDto;
import it.gov.pagopa.nodetsworker.observability.dto.DimensionRowDto;
import it.gov.pagopa.nodetsworker.observability.dto.DurationRowDto;
import it.gov.pagopa.nodetsworker.observability.dto.MatricesDto;
import it.gov.pagopa.nodetsworker.observability.dto.StateCountDto;
import it.gov.pagopa.nodetsworker.observability.dto.TrafficBucketDto;
import it.gov.pagopa.nodetsworker.observability.dto.TransitionTrendDto;
import it.gov.pagopa.nodetsworker.observability.dto.WindowDto;
import it.gov.pagopa.nodetsworker.observability.model.AttemptState;
import it.gov.pagopa.nodetsworker.observability.model.LogicalState;
import it.gov.pagopa.nodetsworker.observability.model.RecipientState;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Builds an aggregate snapshot over a filtered projection and window, a faithful port of
 * {@code aggregate()} in {@code mocks/model.mjs}. Pure and stateless.
 */
public final class AggregationService {

  private AggregationService() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Aggregates the entities with activity in {@code [from, to)} (open on the right when {@code live}),
   * with their current state: every count of the snapshot follows the selected interval.
   */
  public static AggregateDto aggregate(List<DomainPayment> allPayments, Instant from, Instant to,
      Instant now, long revision, boolean live) {
    List<DomainPayment> payments = ProjectionLogic.withinWindow(allPayments, from, to, live);
    List<DomainAttempt> attempts = new ArrayList<>();
    List<DomainRecipient> recipients = new ArrayList<>();
    for (DomainPayment p : payments) {
      attempts.addAll(p.getAttempts());
      recipients.addAll(p.getRecipients());
    }

    AggregateDto dto = new AggregateDto();
    dto.setSnapshotAt(IsoTime.format(now));
    dto.setProjectionRevision(revision);
    dto.setWindow(new WindowDto(IsoTime.format(from), IsoTime.format(to)));
    dto.setCounts(new CountsDto(payments.size(), attempts.size()));
    dto.setLogicalStates(logicalCounts(payments));
    dto.setAttemptStates(attemptCounts(attempts));
    dto.setRecipientStates(recipientCounts(recipients));

    long unresolvedPayments = payments.stream()
        .filter(p -> p.getAttempts().stream().anyMatch(ProjectionLogic::warning)).count();
    long unresolvedAttempts = attempts.stream().filter(ProjectionLogic::warning).count();
    dto.setUnresolved(new CountsDto(unresolvedPayments, unresolvedAttempts));

    dto.setMatrices(new MatricesDto(matrix(attempts, DomainAttempt::getPsp),
        matrix(attempts, DomainAttempt::getCanale), recipientMatrix(recipients)));
    dto.setTraffic(traffic(attempts, from, to, live));
    dto.setTransitions(transitions(payments, attempts, recipients, from, to, live));
    dto.setDurations(durations(payments, attempts, recipients, from, to, live));
    dto.setAnomalies(anomalies(payments, attempts));
    return dto;
  }

  private static boolean inWindow(Instant value, Instant from, Instant to, boolean live) {
    return value != null && !value.isBefore(from) && (live || value.isBefore(to));
  }

  private static List<StateCountDto> logicalCounts(List<DomainPayment> payments) {
    List<StateCountDto> out = new ArrayList<>();
    for (LogicalState state : LogicalState.ORDERED) {
      long count = payments.stream().filter(p -> java.util.Objects.equals(p.currentState(), state.name())).count();
      out.add(new StateCountDto(state.name(), count));
    }
    return out;
  }

  private static List<StateCountDto> attemptCounts(List<DomainAttempt> attempts) {
    List<StateCountDto> out = new ArrayList<>();
    for (AttemptState state : AttemptState.ORDERED) {
      long count = attempts.stream().filter(a -> java.util.Objects.equals(a.currentState(), state.name())).count();
      out.add(new StateCountDto(state.name(), count));
    }
    return out;
  }

  private static List<StateCountDto> recipientCounts(List<DomainRecipient> recipients) {
    List<StateCountDto> out = new ArrayList<>();
    for (RecipientState state : RecipientState.ORDERED) {
      long count = recipients.stream().filter(r -> java.util.Objects.equals(r.currentState(), state.name())).count();
      out.add(new StateCountDto(state.name(), count));
    }
    return out;
  }

  /** Recipient state × {@code stazione_recipient}; {@code value: null} is the unidentified recipient. */
  private static List<DimensionRowDto> recipientMatrix(List<DomainRecipient> recipients) {
    TreeSet<String> values = new TreeSet<>(Comparator.comparing(v -> String.valueOf(v)));
    for (DomainRecipient r : recipients) {
      values.add(r.getStazioneRecipient());
    }
    List<DimensionRowDto> rows = new ArrayList<>();
    for (String value : values) {
      List<DomainRecipient> matching = new ArrayList<>();
      for (DomainRecipient r : recipients) {
        if (java.util.Objects.equals(r.getStazioneRecipient(), value)) {
          matching.add(r);
        }
      }
      rows.add(new DimensionRowDto(value, recipientCounts(matching)));
    }
    return rows;
  }

  private static List<DimensionRowDto> matrix(List<DomainAttempt> attempts, Function<DomainAttempt, String> dim) {
    TreeSet<String> values = new TreeSet<>(Comparator.comparing(v -> String.valueOf(v)));
    for (DomainAttempt a : attempts) {
      values.add(dim.apply(a));
    }
    List<DimensionRowDto> rows = new ArrayList<>();
    for (String value : values) {
      List<DomainAttempt> matching = new ArrayList<>();
      for (DomainAttempt a : attempts) {
        if (java.util.Objects.equals(dim.apply(a), value)) {
          matching.add(a);
        }
      }
      rows.add(new DimensionRowDto(value, attemptCounts(matching)));
    }
    return rows;
  }

  private static List<TrafficBucketDto> traffic(List<DomainAttempt> attempts, Instant from, Instant to,
      boolean live) {
    Duration bucketSize = IsoTime.bucketSize(from, to);
    Map<String, long[]> buckets = new LinkedHashMap<>();
    for (DomainAttempt a : attempts) {
      if (!inWindow(a.getAttemptStartedAt(), from, to, live)) {
        continue;
      }
      String bucket = IsoTime.bucket(a.getAttemptStartedAt(), bucketSize);
      long[] row = buckets.computeIfAbsent(bucket, k -> new long[2]);
      row[0]++;
      if (a.getStartTimestampBasis() == TimestampBasis.INFERRED) {
        row[1]++;
      }
    }
    return buckets.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> new TrafficBucketDto(e.getKey(), e.getValue()[0], e.getValue()[1]))
        .toList();
  }

  private static List<TransitionTrendDto> transitions(List<DomainPayment> payments,
      List<DomainAttempt> attempts, List<DomainRecipient> recipients, Instant from, Instant to,
      boolean live) {
    Duration bucketSize = IsoTime.bucketSize(from, to);
    Map<String, TransitionTrendDto> rows = new LinkedHashMap<>();
    forEachTimeline(payments, attempts, recipients, (entity, timeline) -> {
      for (TimelineItem t : timeline) {
        if (t.getTimestampBasis() == TimestampBasis.ANOMALY || !inWindow(t.getTransitionAt(), from, to, live)) {
          continue;
        }
        String bucket = IsoTime.bucket(t.getTransitionAt(), bucketSize);
        String id = bucket + "/" + entity + "/" + t.getState() + "/" + t.getTimestampBasis();
        TransitionTrendDto row = rows.computeIfAbsent(id,
            k -> new TransitionTrendDto(bucket, entity, t.getState(), t.getTimestampBasis(), 0));
        row.setCount(row.getCount() + 1);
      }
    });
    List<TransitionTrendDto> out = new ArrayList<>(rows.values());
    out.sort(Comparator.comparing(r -> r.getBucketStart() + "/" + r.getEntity() + "/" + r.getState() + "/" + r.getTimestampBasis()));
    return out;
  }

  private static List<DurationRowDto> durations(List<DomainPayment> payments,
      List<DomainAttempt> attempts, List<DomainRecipient> recipients, Instant from, Instant to,
      boolean live) {
    Map<String, DurationAccumulator> acc = new LinkedHashMap<>();
    forEachTimeline(payments, attempts, recipients, (entity, timeline) -> {
      for (int i = 1; i < timeline.size(); i++) {
        TimelineItem a = timeline.get(i - 1);
        TimelineItem b = timeline.get(i);
        if (a.getTransitionAt() == null || b.getTransitionAt() == null || !inWindow(b.getTransitionAt(), from, to, live)) {
          continue;
        }
        long duration = b.getTransitionAt().toEpochMilli() - a.getTransitionAt().toEpochMilli();
        if (duration < 0) {
          continue;
        }
        TimestampBasis basis = (a.getTimestampBasis() == TimestampBasis.INFERRED
            || b.getTimestampBasis() == TimestampBasis.INFERRED)
            ? TimestampBasis.INFERRED : TimestampBasis.SOURCE_EVENT;
        String id = entity + "/" + a.getState() + "/" + b.getState() + "/" + basis;
        acc.computeIfAbsent(id, k -> new DurationAccumulator(entity, a.getState(), b.getState(), basis))
            .samples.add(duration);
      }
    });
    List<DurationRowDto> out = new ArrayList<>();
    for (DurationAccumulator a : acc.values()) {
      out.add(a.toRow());
    }
    return out;
  }

  private static AnomaliesDto anomalies(List<DomainPayment> payments, List<DomainAttempt> attempts) {
    long anomalyTransitions = 0;
    for (DomainPayment p : payments) {
      anomalyTransitions += p.getTimeline().stream().filter(t -> t.getTimestampBasis() == TimestampBasis.ANOMALY).count();
      for (DomainRecipient r : p.getRecipients()) {
        anomalyTransitions += r.getTimeline().stream().filter(t -> t.getTimestampBasis() == TimestampBasis.ANOMALY).count();
      }
    }
    for (DomainAttempt a : attempts) {
      anomalyTransitions += a.getTimeline().stream().filter(t -> t.getTimestampBasis() == TimestampBasis.ANOMALY).count();
    }
    long anomalousPayments = payments.stream().filter(ProjectionLogic::anomalousPayment).count();
    return new AnomaliesDto(anomalyTransitions, anomalousPayments);
  }

  private interface TimelineVisitor {
    void visit(String entity, List<TimelineItem> timeline);
  }

  private static void forEachTimeline(List<DomainPayment> payments, List<DomainAttempt> attempts,
      List<DomainRecipient> recipients, TimelineVisitor visitor) {
    for (DomainPayment p : payments) {
      visitor.visit("logical", p.getTimeline());
    }
    for (DomainAttempt a : attempts) {
      visitor.visit("attempt", a.getTimeline());
    }
    for (DomainRecipient r : recipients) {
      visitor.visit("recipient", r.getTimeline());
    }
  }

  private static final class DurationAccumulator {
    private final String entity;
    private final String fromState;
    private final String toState;
    private final TimestampBasis basis;
    private final List<Long> samples = new ArrayList<>();

    DurationAccumulator(String entity, String fromState, String toState, TimestampBasis basis) {
      this.entity = entity;
      this.fromState = fromState;
      this.toState = toState;
      this.basis = basis;
    }

    DurationRowDto toRow() {
      List<Long> ordered = new ArrayList<>(samples);
      ordered.sort(Comparator.naturalOrder());
      int n = ordered.size();
      double average = ordered.stream().mapToLong(Long::longValue).sum() / (double) n;
      long median = ordered.get((int) Math.ceil(0.5 * n) - 1);
      long p95 = ordered.get((int) Math.ceil(0.95 * n) - 1);
      return new DurationRowDto(entity, fromState, toState, basis, n, average, median, p95);
    }
  }
}
