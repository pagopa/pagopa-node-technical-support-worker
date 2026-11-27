package it.gov.pagopa.nodetsworker.observability.apiconfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Upstream {@code station-maintenances/aggregated} payload (snake_case, offsets as sent by apiconfig). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StationMaintenanceList(
    @JsonProperty("station_maintenance_list") List<Item> items,
    @JsonProperty("page_info") PageInfo pageInfo) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Item(
      @JsonProperty("broker_code") String brokerCode,
      @JsonProperty("broker_name") String brokerName,
      @JsonProperty("start_date_time") String startDateTime,
      @JsonProperty("end_date_time") String endDateTime,
      @JsonProperty("stations") List<String> stations,
      @JsonProperty("creditor_institutions") List<Creditor> creditorInstitutions) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Creditor(
      @JsonProperty("business_name") String businessName,
      @JsonProperty("creditor_institution_code") String code) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PageInfo(@JsonProperty("total_pages") Integer totalPages) {
  }
}
