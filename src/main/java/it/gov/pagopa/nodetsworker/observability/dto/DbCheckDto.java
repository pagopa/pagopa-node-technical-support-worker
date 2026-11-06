package it.gov.pagopa.nodetsworker.observability.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.gov.pagopa.nodetsworker.observability.model.AttemptState;
import it.gov.pagopa.nodetsworker.observability.model.LogicalState;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "DbCheck", description = "Read-only comparison with the routed Nodo Online database")
public class DbCheckDto {
  @Schema(required = true)
  private String attemptId;
  @Schema(required = true, pattern = "^\\d{11}$")
  private String paFiscalCode;
  @Schema(required = true, pattern = "^\\d{18}$")
  private String noticeNumber;
  @Schema(required = true)
  private String serviceIdentifier;
  @Schema(required = true, minimum = "1")
  private long projectionRevision;
  @Schema(required = true, enumeration = {"NODO_ONLINE"})
  private String source;
  @Schema(required = true, format = "date-time",
      pattern = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$")
  private String checkedAt;
  @Schema(required = true)
  private ObservedStates observed;
  @Schema(required = true)
  private DatabaseStates database;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "DbCheckObservedStates")
  public static class ObservedStates {
    @Schema(required = true)
    private LogicalState logicalState;
    @Schema(required = true)
    private AttemptState attemptState;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.ALWAYS)
  @Schema(name = "DbCheckDatabaseStates")
  public static class DatabaseStates {
    @Schema(required = true, nullable = true, description = "null when the logical snapshot is absent")
    private LogicalState logicalState;
    @Schema(required = true, nullable = true, description = "null when the attempt snapshot is absent")
    private AttemptState attemptState;
  }
}
