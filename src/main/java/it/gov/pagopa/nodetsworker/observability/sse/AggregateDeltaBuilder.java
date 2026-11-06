package it.gov.pagopa.nodetsworker.observability.sse;

import it.gov.pagopa.nodetsworker.observability.dto.AggregateDto;
import it.gov.pagopa.nodetsworker.observability.dto.DimensionRowDto;
import it.gov.pagopa.nodetsworker.observability.dto.DurationRowDto;
import it.gov.pagopa.nodetsworker.observability.dto.StateCountDto;
import it.gov.pagopa.nodetsworker.observability.dto.TrafficBucketDto;
import it.gov.pagopa.nodetsworker.observability.dto.TransitionTrendDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code changes} of an {@code aggregate.delta} as signed differences (after − before),
 * a faithful port of {@code compact()}/{@code delta()} in {@code mocks/server.mjs}. Only additive
 * rows with a non-zero change appear; quantiles are non-additive, so {@code durationSnapshot}
 * replaces the whole duration set authoritatively.
 */
public final class AggregateDeltaBuilder {

  private AggregateDeltaBuilder() {
    throw new IllegalStateException("Utility class");
  }

  public static Map<String, Object> changes(AggregateDto before, AggregateDto after) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("counts", counts(before, after));
    out.put("logicalStates", stateDiff(before.getLogicalStates(), after.getLogicalStates()));
    out.put("attemptStates", stateDiff(before.getAttemptStates(), after.getAttemptStates()));
    out.put("recipientStates", stateDiff(before.getRecipientStates(), after.getRecipientStates()));
    out.put("traffic", trafficDiff(before.getTraffic(), after.getTraffic()));
    out.put("transitions", transitionDiff(before.getTransitions(), after.getTransitions()));
    out.put("matrices", matricesDiff(before, after));
    out.put("anomalies", anomalies(before, after));
    out.put("durations", durationDiff(before.getDurations(), after.getDurations()));
    out.put("durationSnapshot", durationSnapshot(after.getDurations()));
    return out;
  }

  private static Map<String, Object> counts(AggregateDto b, AggregateDto a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("payments", a.getCounts().getPayments() - b.getCounts().getPayments());
    m.put("attempts", a.getCounts().getAttempts() - b.getCounts().getAttempts());
    m.put("unresolvedPayments", a.getUnresolved().getPayments() - b.getUnresolved().getPayments());
    m.put("unresolvedAttempts", a.getUnresolved().getAttempts() - b.getUnresolved().getAttempts());
    m.put("anomalyTransitions", a.getAnomalies().getTransitions() - b.getAnomalies().getTransitions());
    return m;
  }

  private static Map<String, Object> anomalies(AggregateDto b, AggregateDto a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("transitions", a.getAnomalies().getTransitions() - b.getAnomalies().getTransitions());
    m.put("payments", a.getAnomalies().getPayments() - b.getAnomalies().getPayments());
    return m;
  }

  private static List<Map<String, Object>> stateDiff(List<StateCountDto> before, List<StateCountDto> after) {
    Map<String, long[]> merged = new LinkedHashMap<>();
    for (StateCountDto r : before) {
      merged.computeIfAbsent(r.getState(), k -> new long[1])[0] -= r.getCount();
    }
    for (StateCountDto r : after) {
      merged.computeIfAbsent(r.getState(), k -> new long[1])[0] += r.getCount();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, long[]> e : merged.entrySet()) {
      if (e.getValue()[0] != 0) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("state", e.getKey());
        row.put("count", e.getValue()[0]);
        out.add(row);
      }
    }
    return out;
  }

  private static List<Map<String, Object>> trafficDiff(List<TrafficBucketDto> before, List<TrafficBucketDto> after) {
    Map<String, long[]> merged = new LinkedHashMap<>();
    for (TrafficBucketDto r : before) {
      long[] v = merged.computeIfAbsent(r.getBucketStart(), k -> new long[2]);
      v[0] -= r.getStartedAttempts();
      v[1] -= r.getEstimatedStarts();
    }
    for (TrafficBucketDto r : after) {
      long[] v = merged.computeIfAbsent(r.getBucketStart(), k -> new long[2]);
      v[0] += r.getStartedAttempts();
      v[1] += r.getEstimatedStarts();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, long[]> e : merged.entrySet()) {
      if (e.getValue()[0] != 0 || e.getValue()[1] != 0) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bucketStart", e.getKey());
        row.put("startedAttempts", e.getValue()[0]);
        row.put("estimatedStarts", e.getValue()[1]);
        out.add(row);
      }
    }
    return out;
  }

  private static List<Map<String, Object>> transitionDiff(List<TransitionTrendDto> before, List<TransitionTrendDto> after) {
    Map<String, long[]> merged = new LinkedHashMap<>();
    Map<String, TransitionTrendDto> keys = new LinkedHashMap<>();
    for (TransitionTrendDto r : before) {
      String id = r.getBucketStart() + "|" + r.getEntity() + "|" + r.getState() + "|" + r.getTimestampBasis();
      keys.putIfAbsent(id, r);
      merged.computeIfAbsent(id, k -> new long[1])[0] -= r.getCount();
    }
    for (TransitionTrendDto r : after) {
      String id = r.getBucketStart() + "|" + r.getEntity() + "|" + r.getState() + "|" + r.getTimestampBasis();
      keys.putIfAbsent(id, r);
      merged.computeIfAbsent(id, k -> new long[1])[0] += r.getCount();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, long[]> e : merged.entrySet()) {
      if (e.getValue()[0] != 0) {
        TransitionTrendDto k = keys.get(e.getKey());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bucketStart", k.getBucketStart());
        row.put("entity", k.getEntity());
        row.put("state", k.getState());
        row.put("timestampBasis", k.getTimestampBasis());
        row.put("count", e.getValue()[0]);
        out.add(row);
      }
    }
    return out;
  }

  private static Map<String, Object> matricesDiff(AggregateDto b, AggregateDto a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("psp", dimensionDiff(b.getMatrices().getPsp(), a.getMatrices().getPsp()));
    m.put("canale", dimensionDiff(b.getMatrices().getCanale(), a.getMatrices().getCanale()));
    m.put("recipient", dimensionDiff(b.getMatrices().getRecipient(), a.getMatrices().getRecipient()));
    return m;
  }

  private static List<Map<String, Object>> dimensionDiff(List<DimensionRowDto> before, List<DimensionRowDto> after) {
    Map<String, long[]> merged = new LinkedHashMap<>();
    Map<String, String[]> keys = new LinkedHashMap<>();
    accumulateDimension(before, merged, keys, -1);
    accumulateDimension(after, merged, keys, 1);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, long[]> e : merged.entrySet()) {
      if (e.getValue()[0] != 0) {
        String[] k = keys.get(e.getKey());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", k[0]);
        row.put("state", k[1]);
        row.put("count", e.getValue()[0]);
        out.add(row);
      }
    }
    return out;
  }

  private static void accumulateDimension(List<DimensionRowDto> rows, Map<String, long[]> merged,
      Map<String, String[]> keys, int sign) {
    for (DimensionRowDto dim : rows) {
      for (StateCountDto s : dim.getStates()) {
        String id = dim.getValue() + "|" + s.getState();
        keys.putIfAbsent(id, new String[] {dim.getValue(), s.getState()});
        merged.computeIfAbsent(id, k -> new long[1])[0] += sign * s.getCount();
      }
    }
  }

  private static List<Map<String, Object>> durationDiff(List<DurationRowDto> before, List<DurationRowDto> after) {
    Map<String, double[]> merged = new LinkedHashMap<>();
    Map<String, DurationRowDto> keys = new LinkedHashMap<>();
    accumulateDuration(before, merged, keys, -1);
    accumulateDuration(after, merged, keys, 1);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, double[]> e : merged.entrySet()) {
      double samples = e.getValue()[0];
      double totalMs = e.getValue()[1];
      if (samples != 0 || totalMs != 0) {
        DurationRowDto k = keys.get(e.getKey());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entity", k.getEntity());
        row.put("fromState", k.getFromState());
        row.put("toState", k.getToState());
        row.put("timestampBasis", k.getTimestampBasis());
        row.put("samples", (long) samples);
        row.put("totalMs", totalMs);
        out.add(row);
      }
    }
    return out;
  }

  private static void accumulateDuration(List<DurationRowDto> rows, Map<String, double[]> merged,
      Map<String, DurationRowDto> keys, int sign) {
    for (DurationRowDto r : rows) {
      String id = r.getEntity() + "|" + r.getFromState() + "|" + r.getToState() + "|" + r.getTimestampBasis();
      keys.putIfAbsent(id, r);
      double[] v = merged.computeIfAbsent(id, k -> new double[2]);
      v[0] += sign * r.getSamples();
      v[1] += sign * r.getAverageMs() * r.getSamples();
    }
  }

  private static List<Map<String, Object>> durationSnapshot(List<DurationRowDto> durations) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (DurationRowDto r : durations) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("entity", r.getEntity());
      row.put("fromState", r.getFromState());
      row.put("toState", r.getToState());
      row.put("timestampBasis", r.getTimestampBasis());
      row.put("samples", r.getSamples());
      row.put("averageMs", r.getAverageMs());
      row.put("medianMs", r.getMedianMs());
      row.put("p95Ms", r.getP95Ms());
      out.add(row);
    }
    return out;
  }
}
