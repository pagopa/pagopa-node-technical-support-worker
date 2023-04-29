package it.gov.pagopa.nodetsworker.resources;

import it.gov.pagopa.nodetsworker.models.ProblemJson;
import it.gov.pagopa.nodetsworker.models.TransactionResponse;
import it.gov.pagopa.nodetsworker.services.WorkerService;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.Serializable;
import java.util.Date;

@Path("/organizations")
@Produces(value = MediaType.APPLICATION_JSON)
public class WorkerResource implements Serializable {

    @Inject
    WorkerService workerService;

    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransactionResponse.class))),
            @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProblemJson.class))),
            @APIResponse(responseCode = "500", description = "Service unavailable.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProblemJson.class)))
    })
    @GET
    @Path("/{organizationFiscalCode}/noticeNumber/{noticeNumber}")
    public Response useCaseSP03_byNoticeNumber(
            @PathParam("organizationFiscalCode") String organizationFiscalCode,
            @PathParam("noticeNumber") String noticeNumber,
            @QueryParam("dateFrom") Date dateFrom,
            @QueryParam("dateTo") Date dateTo
    ) {
        return Response.ok(workerService.getInfoByNoticeNumber(organizationFiscalCode, noticeNumber, dateFrom, dateTo)).build();
    }


}
