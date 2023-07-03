package it.gov.pagopa.nodetsworker.resources;

import it.gov.pagopa.nodetsworker.models.ProblemJson;
import it.gov.pagopa.nodetsworker.models.TransactionResponse;
import it.gov.pagopa.nodetsworker.service.WorkerService;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.Serializable;
import java.time.LocalDate;

@Path("/organizations")
@Produces(value = MediaType.APPLICATION_JSON)
public class WorkerResource implements Serializable {

    @Inject
    WorkerService workerService;

    /**
     *  ######  ########    #####    #######
     * ##    ## ##     ##  ##   ##  ##     ##
     * ##       ##     ## ##     ##        ##
     *  ######  ########  ##     ##  #######
     *       ## ##        ##     ##        ##
     * ##    ## ##         ##   ##  ##     ##
     *  ######  ##          #####    #######
     */
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransactionResponse.class))),
            @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProblemJson.class))),
            @APIResponse(responseCode = "500", description = "Service unavailable.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProblemJson.class)))
    })
    @GET
    @Path("/{organizationFiscalCode}/noticeNumber/{noticeNumber}")
    public Response useCaseSP03_byNoticeNumber(
            @PathParam("organizationFiscalCode") @NotNull String organizationFiscalCode,
            @PathParam("noticeNumber") @NotNull String noticeNumber,
            @QueryParam("dateFrom") LocalDate dateFrom,
            @QueryParam("dateTo") LocalDate dateTo
    ) {
        return Response.ok(workerService.getInfoByNoticeNumber(organizationFiscalCode, noticeNumber, dateFrom, dateTo)).build();
    }

    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransactionResponse.class))),
            @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProblemJson.class))),
            @APIResponse(responseCode = "500", description = "Service unavailable.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProblemJson.class)))
    })
    @GET
    @Path("/{organizationFiscalCode}/iuv/{iuv}")
    public Response useCaseSP03_byIUV(
            @PathParam("organizationFiscalCode") @NotNull String organizationFiscalCode,
            @PathParam("iuv") @NotNull String iuv,
            @QueryParam("dateFrom") LocalDate dateFrom,
            @QueryParam("dateTo") LocalDate dateTo
    ) {
        return Response.ok(workerService.getInfoByIUV(organizationFiscalCode, iuv, dateFrom, dateTo)).build();
    }

    /**
     *  ######  ########    #####   ##
     * ##    ## ##     ##  ##   ##  ##    ##
     * ##       ##     ## ##     ## ##    ##
     *  ######  ########  ##     ## ##    ##
     *       ## ##        ##     ## #########
     * ##    ## ##         ##   ##        ##
     *  ######  ##          #####         ##
     */

    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransactionResponse.class))),
            @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProblemJson.class))),
            @APIResponse(responseCode = "500", description = "Service unavailable.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProblemJson.class)))
    })
    @GET
    @Path("/{organizationFiscalCode}/iuv/{iuv}/paymentToken/{paymentToken}")
    public Response useCaseSP04_byIUV_PaymentToken(
            @PathParam("organizationFiscalCode") @NotNull String organizationFiscalCode,
            @PathParam("iuv") @NotNull String iuv,
            @PathParam("paymentToken") @NotNull String paymentToken,
            @QueryParam("dateFrom") LocalDate dateFrom,
            @QueryParam("dateTo") LocalDate dateTo
    ) {
        return Response.ok(workerService.getInfoByNoticeNumberAndPaymentToken(organizationFiscalCode, iuv, paymentToken, dateFrom, dateTo)).build();
    }

    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "OK", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = TransactionResponse.class))),
            @APIResponse(responseCode = "400", description = "Bad Request", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProblemJson.class))),
            @APIResponse(responseCode = "500", description = "Service unavailable.", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ProblemJson.class)))
    })
    @GET
    @Path("/{organizationFiscalCode}/iuv/{iuv}/ccp/{ccp}")
    public Response useCaseSP04_byIUV_CCP(
            @PathParam("organizationFiscalCode") @NotNull String organizationFiscalCode,
            @PathParam("iuv") @NotNull String iuv,
            @PathParam("ccp") @NotNull String ccp,
            @QueryParam("dateFrom") LocalDate dateFrom,
            @QueryParam("dateTo") LocalDate dateTo
    ) {
        return Response.ok(workerService.getAttemptByIUVAndCCP(organizationFiscalCode, iuv, ccp, dateFrom, dateTo)).build();
    }
}
