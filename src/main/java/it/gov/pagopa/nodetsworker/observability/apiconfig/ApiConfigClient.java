package it.gov.pagopa.nodetsworker.observability.apiconfig;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** apiconfig API (through APIM) listing the scheduled station maintenances of the brokers. */
@RegisterRestClient(configKey = "apiconfig")
@Produces(MediaType.APPLICATION_JSON)
public interface ApiConfigClient {

  /** A null subscription key omits the header. */
  @GET
  @Path("/brokers/station-maintenances/aggregated")
  StationMaintenanceList aggregates(
      @QueryParam("startDateTimeAfter") String startDateTimeAfter,
      @QueryParam("endDateTimeBefore") String endDateTimeBefore,
      @HeaderParam("Ocp-Apim-Subscription-Key") String subscriptionKey);
}
