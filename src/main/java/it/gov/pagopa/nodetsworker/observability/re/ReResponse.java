package it.gov.pagopa.nodetsworker.observability.re;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "ReResponse", requiredProperties = {"attemptId", "source", "retrievedAt", "window",
    "coverage", "events", "nextCursor", "truncated", "warnings"})
public record ReResponse(String attemptId,
                         @Schema(enumeration = {"RE"}) String source,
                         @Schema(format = "date-time") String retrievedAt, Window window,
                         @Schema(enumeration = {"WINDOW_ONLY", "RETENTION_CLIPPED", "LIMIT_REACHED"})
                         String coverage, @Schema(maxItems = 100) List<ReEvent> events,
                         @Schema(nullable = true) String nextCursor,
                         boolean truncated, List<String> warnings) {
  @Schema(name = "ReWindow", requiredProperties = {"from", "to"})
  public record Window(String from, String to) { }

  @Override
  public String toString() {
    return "ReResponse[redacted]";
  }
}
