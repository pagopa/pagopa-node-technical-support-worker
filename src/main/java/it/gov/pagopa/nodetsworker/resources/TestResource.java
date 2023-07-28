package it.gov.pagopa.nodetsworker.resources;

import it.gov.pagopa.nodetsworker.service.WorkerService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.Serializable;

@Path("/test")
@Produces(value = MediaType.APPLICATION_JSON)
public class TestResource implements Serializable {

  @Inject WorkerService workerService;

  @GET
  @Path("/partitionkey/{partitionKey}")
  public Response countByPartitionKey(
      @PathParam("partitionKey") @NotNull String partitionKey) {
    return Response.ok(
            workerService.countByPartitionKey(partitionKey))
        .build();
  }
}
