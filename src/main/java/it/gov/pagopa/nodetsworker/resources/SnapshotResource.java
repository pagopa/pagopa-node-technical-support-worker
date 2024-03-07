package it.gov.pagopa.nodetsworker.resources;

import it.gov.pagopa.nodetsworker.models.ProblemJson;
import it.gov.pagopa.nodetsworker.resources.response.PaymentResponse;
import it.gov.pagopa.nodetsworker.service.SnapshotService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.Min;
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

@Path("/snapshot/organizations")
@Produces(value = MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SnapshotResource implements Serializable {

    private final SnapshotService snapshotService;

    public SnapshotResource(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @APIResponses(
            value = {
                    @APIResponse(
                            responseCode = "200",
                            description = "OK",
                            content =
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON,
                                    schema = @Schema(implementation = PaymentResponse.class))),
                    @APIResponse(
                            responseCode = "400",
                            description = "Bad Request",
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
    @Path("/{organizationFiscalCode}")
    public Response positionPaymentStatusSnapshot(
            @PathParam("organizationFiscalCode") @NotNull String organizationFiscalCode,
            @QueryParam("noticeNumber") String noticeNumber,
            @QueryParam("paymentToken") String paymentToken,
            @QueryParam("dateFrom") LocalDate dateFrom,
            @QueryParam("dateTo") LocalDate dateTo,
            @QueryParam("page") @DefaultValue("1") @Min(value = 1) long pageNumber,
            @QueryParam("size") @DefaultValue("1000") @Min(value = 1) long pageSize) {

        PaymentResponse response = snapshotService.getPosPaymentStatusSnapshot(organizationFiscalCode, noticeNumber, paymentToken, dateFrom, dateTo, pageNumber, pageSize);
        return Response.ok(response).build();
    }
}
