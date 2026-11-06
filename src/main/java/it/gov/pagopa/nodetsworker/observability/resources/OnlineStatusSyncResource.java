package it.gov.pagopa.nodetsworker.observability.resources;

import it.gov.pagopa.nodetsworker.observability.dto.AttemptSyncRequestDto;
import it.gov.pagopa.nodetsworker.observability.dto.PaymentSyncRequestDto;
import it.gov.pagopa.nodetsworker.observability.dto.StatusSyncResponseDto;
import it.gov.pagopa.nodetsworker.observability.service.OnlineStatusSyncService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.sql.SQLException;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

/** Internal endpoints for correcting observability state from Nodo Online. */
@Path("/internal/observability/sync")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NoStore
public class OnlineStatusSyncResource {

  @Inject
  OnlineStatusSyncService service;

  @POST
  @Path("/payment")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Synchronization outcome",
          content = @Content(schema = @Schema(implementation = StatusSyncResponseDto.class))),
      @APIResponse(responseCode = "400", description = "Invalid request"),
      @APIResponse(responseCode = "500", description = "Database or server error"),
      @APIResponse(responseCode = "502", description = "Unsupported state returned by Nodo Online")
  })
  public StatusSyncResponseDto syncPayment(
      @org.eclipse.microprofile.openapi.annotations.parameters.RequestBody(
          description = "Payment key", required = true)
      @Valid @NotNull PaymentSyncRequestDto request) throws SQLException {
    OnlineStatusSyncService.SyncResult result =
        service.syncPayment(request.getPaFiscalCode(), request.getNoticeNumber());
    return response(result);
  }

  @POST
  @Path("/attempt")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Synchronization outcome",
          content = @Content(schema = @Schema(implementation = StatusSyncResponseDto.class))),
      @APIResponse(responseCode = "400", description = "Invalid request"),
      @APIResponse(responseCode = "500", description = "Database or server error"),
      @APIResponse(responseCode = "502", description = "Unsupported state returned by Nodo Online")
  })
  public StatusSyncResponseDto syncAttempt(
      @org.eclipse.microprofile.openapi.annotations.parameters.RequestBody(
          description = "Payment key and attempt token", required = true)
      @Valid @NotNull AttemptSyncRequestDto request) throws SQLException {
    OnlineStatusSyncService.SyncResult result =
        service.syncAttempt(request.getPaFiscalCode(), request.getNoticeNumber(), request.getPaymentToken());
    return response(result);
  }

  private static StatusSyncResponseDto response(OnlineStatusSyncService.SyncResult result) {
    return new StatusSyncResponseDto(result.changed() ? "SYNCED" : "NOOP", result.state());
  }
}
