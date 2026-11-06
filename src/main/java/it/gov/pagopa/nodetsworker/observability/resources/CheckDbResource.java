package it.gov.pagopa.nodetsworker.observability.resources;

import it.gov.pagopa.nodetsworker.observability.dto.DbCheckDto;
import it.gov.pagopa.nodetsworker.observability.dto.ErrorDto;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.service.CallerResolver;
import it.gov.pagopa.nodetsworker.observability.service.DbCheckService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@NoStore
public class CheckDbResource {
  @Inject DbCheckService service;
  @Inject CallerResolver callers;

  @GET
  @Path("/attempts/{attemptId}/check-db")
  @Operation(operationId = "checkAttemptDatabase", summary = "Compare attempt and payment states with Nodo Online",
      description = "Read-only. Resolves token, PA/NAV and datasource from the retained attempt, "
          + "using the existing serviceIdentifier routing. No query parameters or projection writes. "
          + "Missing Online snapshots are independent null values; duplicate or unsupported snapshots fail explicitly. "
          + "projectionRevision identifies the captured observation, not a distributed transaction across both databases.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Real Nodo Online comparison, including absent snapshots",
          content = @Content(schema = @Schema(implementation = DbCheckDto.class))),
      @APIResponse(responseCode = "400", description = "Query parameters are not accepted",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "404", description = "Attempt absent, outside caller visibility or expired",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "422", description = "DB_SERVICE_MISSING or DB_SERVICE_UNCONFIGURED",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "500", description = "Incomplete projection, duplicate or invalid snapshot, or server error",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "503", description = "DB_UNAVAILABLE: database failure or timeout, no partial result",
          content = @Content(schema = @Schema(implementation = ErrorDto.class)))
  })
  public DbCheckDto check(@PathParam("attemptId") String attemptId, @Context UriInfo uriInfo) {
    if (!uriInfo.getQueryParameters().isEmpty()) {
      throw Errors.invalidRequest();
    }
    return service.check(callers.resolve(), attemptId);
  }
}
