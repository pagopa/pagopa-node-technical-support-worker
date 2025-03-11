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
import java.util.Optional;

@Path("/organizations")
@Produces(value = MediaType.APPLICATION_JSON)
public class WorkerResource implements Serializable {

  private final WorkerService workerService;

  public WorkerResource(WorkerService workerService) {
      this.workerService = workerService;
  }

  /** SP03 */
  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PaymentsResponse.class))),
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
  @Path("/{organizationFiscalCode}/noticeNumber/{noticeNumber}")
  public Response useCaseSP03_byNoticeNumber(
      @PathParam("organizationFiscalCode") @NotNull String organizationFiscalCode,
      @PathParam("noticeNumber") @NotNull String noticeNumber,
      @QueryParam("dateFrom") LocalDate dateFrom,
      @QueryParam("dateTo") LocalDate dateTo) {
    return Response.ok(
            workerService.getInfoByNoticeNumber(organizationFiscalCode, noticeNumber, Optional.empty(),dateFrom, dateTo)
            )
        .build();
  }

  @APIResponses(
      value = {
        @APIResponse(
            responseCode = "200",
            description = "OK",
            content =
                @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PaymentsResponse.class))),
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
  @Path("/{organizationFiscalCode}/iuv/{iuv}")
  public Response useCaseSP03_byIUV(
      @PathParam("organizationFiscalCode") @NotNull String organizationFiscalCode,
      @PathParam("iuv") @NotNull String iuv,
      @QueryParam("dateFrom") LocalDate dateFrom,
      @QueryParam("dateTo") LocalDate dateTo) {
    return Response.ok(workerService.getInfoByIUV(organizationFiscalCode, iuv, dateFrom, dateTo))
        .build();
  }

  /** SP04 */
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
  @Path("/{organizationFiscalCode}/noticeNumber/{noticeNumber}/paymentToken/{paymentToken}")
  public Response useCaseSP04_byIUV_PaymentToken(
      @PathParam("organizationFiscalCode") @NotNull String organizationFiscalCode,
      @PathParam("noticeNumber") @NotNull String noticeNumber,
      @PathParam("paymentToken") @NotNull String paymentToken,
      @QueryParam("dateFrom") LocalDate dateFrom,
      @QueryParam("dateTo") LocalDate dateTo) {
    return Response.ok(
                    workerService.getPaymentsFullByNoticeNumberAndPaymentToken(organizationFiscalCode, noticeNumber, paymentToken,dateFrom, dateTo))
        .build();
  }

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
  @Path("/{organizationFiscalCode}/iuv/{iuv}/ccp/{ccp}")
  public Response useCaseSP04_byIUV_CCP(
      @PathParam("organizationFiscalCode") @NotNull String organizationFiscalCode,
      @PathParam("iuv") @NotNull String iuv,
      @PathParam("ccp") @NotNull String ccp,
      @QueryParam("dateFrom") LocalDate dateFrom,
      @QueryParam("dateTo") LocalDate dateTo) {
    return Response.ok(
            workerService.getPaymentsFullByIUVAndCCP(organizationFiscalCode, iuv, ccp, dateFrom, dateTo))
        .build();
  }
}
