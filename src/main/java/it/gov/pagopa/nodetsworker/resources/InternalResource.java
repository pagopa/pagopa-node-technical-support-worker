package it.gov.pagopa.nodetsworker.resources;

import it.gov.pagopa.nodetsworker.models.ProblemJson;
import it.gov.pagopa.nodetsworker.resources.response.PaymentsFullResponse;
import it.gov.pagopa.nodetsworker.resources.response.PaymentsResponse;
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
import java.util.Map;
import java.util.Optional;

@Path("/internal")
@Produces(value = MediaType.APPLICATION_JSON)
public class InternalResource implements Serializable {

  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = Map.class)))
      })
  @PUT
  @Path("/cache/{key}/{value}")
  public Response setCacheValue(
      @PathParam("key") @NotNull String key,
      @PathParam("value") @NotNull String value) {
    return Response.ok().build();
  }

    @APIResponses(
            value = {
                    @APIResponse(
                            responseCode = "200",
                            description = "OK",
                            content =
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON
                            )
                    )
            })
    @GET
    @Path("/cache/{key}")
    public Response getCacheValue(
            @PathParam("key") @NotNull String key
    ) {
        return Response.ok().build();
    }


  @APIResponses(
            value = {
                    @APIResponse(
                            responseCode = "200",
                            description = "OK",
                            content =
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON
                            )
                    )
            })
    @DELETE
    @Path("/cache/{key}")
    public Response deleteCacheValue(
            @PathParam("key") @NotNull String key
    ) {
        return Response.ok().build();
    }

}
