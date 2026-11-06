package it.gov.pagopa.nodetsworker.observability.resources;

import it.gov.pagopa.nodetsworker.observability.dto.ErrorDto;
import it.gov.pagopa.nodetsworker.observability.dto.MaintenancesDto;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.apiconfig.MaintenanceCache;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Path("/api/apiconfig")
@Produces(MediaType.APPLICATION_JSON)
@NoStore
public class ApiConfigResource {
  @Inject
  MaintenanceCache cache;

  @GET
  @Path("/maintenances")
  @Operation(operationId = "getMaintenances", summary = "Scheduled station maintenances of the next 7 days",
      description = "Read-only. Served from a cache that is reloaded from apiconfig every midnight.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Cached maintenances",
          content = @Content(schema = @Schema(implementation = MaintenancesDto.class))),
      @APIResponse(responseCode = "400", description = "Query parameters are not accepted",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "503", description = "apiconfig never answered: no data available",
          content = @Content(schema = @Schema(implementation = ErrorDto.class)))
  })
  public MaintenancesDto maintenances(@Context UriInfo uriInfo) {
    if (!uriInfo.getQueryParameters().isEmpty()) {
      throw Errors.invalidRequest();
    }
    return cache.current().orElseThrow(Errors::maintenanceUnavailable);
  }
}
