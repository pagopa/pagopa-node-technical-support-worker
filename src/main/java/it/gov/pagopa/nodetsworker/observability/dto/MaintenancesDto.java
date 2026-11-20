package it.gov.pagopa.nodetsworker.observability.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "Maintenances", description = "Scheduled station maintenances of the next 7 days, cached daily")
public class MaintenancesDto {
  @Schema(required = true, format = "date-time", description = "Last successful load from apiconfig",
      pattern = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$")
  private String fetchedAt;
  @Schema(required = true, format = "date-time")
  private String windowFrom;
  @Schema(required = true, format = "date-time", description = "windowFrom + 7 days")
  private String windowTo;
  @Schema(required = true, description = "True when the last refresh failed and the previous data is served")
  private boolean stale;
  @Schema(required = true)
  private List<Maintenance> items;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "Maintenance")
  public static class Maintenance {
    @Schema(required = true)
    private String brokerCode;
    @Schema(required = true)
    private String brokerName;
    @Schema(required = true, format = "date-time")
    private String startDateTime;
    @Schema(required = true, format = "date-time")
    private String endDateTime;
    @Schema(required = true)
    private List<String> stations;
    @Schema(required = true)
    private List<Creditor> creditorInstitutions;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "MaintenanceCreditor")
  public static class Creditor {
    @Schema(required = true)
    private String businessName;
    @Schema(required = true)
    private String code;
  }
}
