package it.gov.pagopa.nodetsworker.observability.domain;

import it.gov.pagopa.nodetsworker.observability.dto.PrimitiveCountDto;
import it.gov.pagopa.nodetsworker.observability.dto.PrimitiveWarningDto;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure derivations over the primitive counters (spec §3.3.2). The same predicate selects buckets for
 * the snapshot and for the SSE delta, so a snapshot plus its deltas always agree.
 *
 * <p>Counters have no attempt state or creditor dimension: the {@code serviceIdentifier}, {@code psp},
 * {@code canale} and {@code stazione} filters apply, the state/anomaly/PA filters do not, and a
 * creditor-restricted caller gets no primitives (fail closed).
 */
public final class PrimitiveLogic {

  /** Counters are bucketed by minute of the request. */
  public static final ChronoUnit BUCKET = ChronoUnit.MINUTES;

  private PrimitiveLogic() {
    throw new IllegalStateException("Utility class");
  }

  public static Instant bucketOf(Instant instant) {
    return instant.truncatedTo(BUCKET);
  }

  /** A bucket belongs to {@code [from, to)} when its minute starts inside the window's minutes. */
  public static boolean inWindow(Instant bucketStart, Instant from, Instant to) {
    return !bucketStart.isBefore(bucketOf(from)) && bucketStart.isBefore(to);
  }

  private static boolean match(String value, String wanted) {
    if (wanted == null) {
      return true;
    }
    if (Filters.UNKNOWN.equals(wanted)) {
      return value == null;
    }
    return Objects.equals(value, wanted);
  }

  public static List<PrimitiveCountDto> rows(List<PrimitiveBucket> buckets, Filters filters,
      Instant from, Instant to, String allowedPa) {
    return rows(buckets, filters, from, to, allowedPa, false);
  }

  /**
   * Counters per {@code serviceIdentifier}, primitive and direction for the window and filters; empty for a
   * restricted caller. A {@code live} window has no upper bound, so requests after {@code to} count.
   */
  public static List<PrimitiveCountDto> rows(List<PrimitiveBucket> buckets, Filters filters,
      Instant from, Instant to, String allowedPa, boolean live) {
    if (allowedPa != null) {
      return List.of();
    }
    record Group(String service, String primitive, PrimitiveDirection direction) {
    }
    Map<Group, long[]> sums = new LinkedHashMap<>();
    for (PrimitiveBucket bucket : buckets) {
      if (!inWindow(bucket.bucketStart(), from, live ? Instant.MAX : to)
          || !match(bucket.serviceIdentifier(), filters.getServiceIdentifier())
          || !match(bucket.psp(), filters.getPsp())
          || !match(bucket.canale(), filters.getCanale())
          || !match(bucket.stazione(), filters.getStazione())) {
        continue;
      }
      long[] sum = sums.computeIfAbsent(new Group(bucket.serviceIdentifier(), bucket.primitive(), bucket.direction()),
          k -> new long[3]);
      sum[0] += bucket.requests();
      sum[1] += bucket.responses();
      sum[2] += bucket.warnings();
    }
    List<PrimitiveCountDto> out = new ArrayList<>();
    for (Map.Entry<Group, long[]> e : sums.entrySet()) {
      long[] sum = e.getValue();
      if (sum[0] != 0 || sum[1] != 0 || sum[2] != 0) {
        out.add(new PrimitiveCountDto(e.getKey().service(), e.getKey().primitive(), e.getKey().direction().name(),
            sum[0], sum[1], sum[2]));
      }
    }
    out.sort(Comparator.comparing(PrimitiveCountDto::getServiceIdentifier,
        Comparator.nullsFirst(Comparator.<String>naturalOrder()))
        .thenComparing(PrimitiveCountDto::getPrimitive)
        .thenComparing(PrimitiveCountDto::getDirection));
    return out;
  }

  /**
   * The calls behind the {@code warnings} counter of one primitive, newest first. The window and the
   * dimension filters are the ones of {@link #rows}, so the list and the counter always agree: a call
   * belongs to the minute of its request, and a restricted caller gets nothing.
   */
  public static List<PrimitiveWarningDto> warnings(List<PrimitiveWarningRow> calls, String primitive,
      Filters filters, Instant from, Instant to, String allowedPa, boolean live) {
    if (allowedPa != null) {
      return List.of();
    }
    return calls.stream()
        .filter(call -> primitive.equals(call.primitive())
            && inWindow(bucketOf(call.calledAt()), from, live ? Instant.MAX : to)
            && match(call.serviceIdentifier(), filters.getServiceIdentifier())
            && match(call.psp(), filters.getPsp())
            && match(call.canale(), filters.getCanale())
            && match(call.stazione(), filters.getStazione()))
        .sorted(Comparator.comparing(PrimitiveWarningRow::calledAt).reversed()
            .thenComparing(PrimitiveWarningRow::paFiscalCode, Comparator.nullsFirst(Comparator.<String>naturalOrder()))
            .thenComparing(PrimitiveWarningRow::stazione, Comparator.nullsFirst(Comparator.<String>naturalOrder()))
            .thenComparing(PrimitiveWarningRow::psp, Comparator.nullsFirst(Comparator.<String>naturalOrder())))
        .map(call -> new PrimitiveWarningDto(IsoTime.format(call.calledAt()), call.psp(), call.canale(),
            call.paFiscalCode(), call.stazione()))
        .toList();
  }
}
