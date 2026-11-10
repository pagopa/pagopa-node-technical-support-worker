package it.gov.pagopa.nodetsworker.observability.domain;

import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Primitive counters of one minute and one dimension combination, or a signed difference of them
 * when it travels in an outbox row. Dimensions are {@code null} when not available.
 */
public record PrimitiveBucket(Instant bucketStart, String serviceIdentifier, String primitive, String psp, String canale,
    String stazione, long requests, long responses, long warnings, PrimitiveDirection direction) {

  /** A counter whose direction is not known. */
  public PrimitiveBucket(Instant bucketStart, String serviceIdentifier, String primitive, String psp, String canale,
      String stazione, long requests, long responses, long warnings) {
    this(bucketStart, serviceIdentifier, primitive, psp, canale, stazione, requests, responses, warnings,
        PrimitiveDirection.UNKNOWN);
  }

  /** JSON-friendly form stored in {@code outbox_event.primitive_changes}. */
  public static List<Map<String, Object>> toMaps(List<PrimitiveBucket> buckets) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (PrimitiveBucket b : buckets) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("bucketStart", IsoTime.format(b.bucketStart()));
      row.put("serviceIdentifier", b.serviceIdentifier());
      row.put("primitive", b.primitive());
      row.put("direction", b.direction().name());
      row.put("psp", b.psp());
      row.put("canale", b.canale());
      row.put("stazione", b.stazione());
      row.put("requests", b.requests());
      row.put("responses", b.responses());
      row.put("warnings", b.warnings());
      out.add(row);
    }
    return out;
  }

  public static List<PrimitiveBucket> fromMaps(List<Map<String, Object>> rows) {
    List<PrimitiveBucket> out = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      out.add(new PrimitiveBucket(IsoTime.parse((String) row.get("bucketStart")),
          (String) row.get("serviceIdentifier"), (String) row.get("primitive"), (String) row.get("psp"), (String) row.get("canale"),
          (String) row.get("stazione"), ((Number) row.get("requests")).longValue(),
          ((Number) row.get("responses")).longValue(), ((Number) row.get("warnings")).longValue(), PrimitiveDirection.parse((String) row.get("direction"))));
    }
    return out;
  }
}
