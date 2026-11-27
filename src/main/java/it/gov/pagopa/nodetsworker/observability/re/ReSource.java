package it.gov.pagopa.nodetsworker.observability.re;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Dedicated read-only RE boundary; seek keys are technical metadata, never event IDs. */
public interface ReSource {
  List<Row> read(Query query);

  /** A null {@code token} selects the whole position (PA + NAV), including events carrying no token. */
  record Query(String pa, String notice, String token, Instant from, Instant to, Instant watermark,
               Instant afterTime, String afterId, int take) {
    @Override
    public String toString() {
      return "ReQuery[redacted]";
    }
  }

  record Row(Instant time, String id, Map<String, Object> fields) {
    @Override
    public String toString() {
      return "ReRow[redacted]";
    }
  }
}
