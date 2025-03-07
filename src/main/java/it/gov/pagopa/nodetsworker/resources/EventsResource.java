package it.gov.pagopa.nodetsworker.resources;

import it.gov.pagopa.nodetsworker.models.ProblemJson;
import it.gov.pagopa.nodetsworker.resources.response.PaymentsFullResponse;
import it.gov.pagopa.nodetsworker.service.WorkerService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Optional;

@Path("/events")
@Produces(value = MediaType.APPLICATION_JSON)
public class EventsResource implements Serializable {

  @Inject WorkerService workerService;

  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PaymentsFullResponse.class))),
        @APIResponse(
            responseCode = "404",
            description = "Not found",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ProblemJson.class))),
        @APIResponse(
            responseCode = "500",
            description = "Service unavailable.",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ProblemJson.class)))
      })
  @GET
  @Path("/negative/{bizEventId}")
  public Response getNegativeBizEventById(
      @PathParam("bizEventId") @NotNull String bizEventId) {
    return Response.ok(
            workerService.getNegativeBizEventById(bizEventId))
        .build();
  }
}
