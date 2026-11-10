package it.gov.pagopa.nodetsworker.observability.resources;

import com.fasterxml.jackson.databind.JsonNode;
import it.gov.pagopa.nodetsworker.observability.dto.ErrorDto;
import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import it.gov.pagopa.nodetsworker.observability.re.ReDiagnostics;
import it.gov.pagopa.nodetsworker.observability.re.ReRequest;
import it.gov.pagopa.nodetsworker.observability.re.ReResponse;
import it.gov.pagopa.nodetsworker.observability.service.CallerResolver;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.util.Set;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NoStore
public class ReEventsResource {
  @Inject ReDiagnostics diagnostics;
  @Inject CallerResolver callers;

  @POST
  @Path("/attempts/{attemptId}/re-events")
  @Operation(operationId = "readAttemptReEvents", summary = "Consult a bounded RE diagnostic window",
      description = "Read-only; disabled until deployment verification and personal-data approval. "
          + "Explicit UTC [from,to), maximum 24h. Resolves PA/NAV/token and expiry server-side. "
          + "All RE events matching the AND keys are included, without FSM/primitive filters. "
          + "With scope POSITION the paymentToken key is dropped: events of the whole PA + NAV pair, "
          + "including token-less calls such as verifyPaymentNotice, are returned. "
          + "Coverage never means full history; retained windows may be clipped. "
          + "Signed opaque seek cursors freeze the ingestion watermark and expire after at most 300s. "
          + "Maximum 100 events/page, 1000 events/consultation, 5MiB JSON/response. "
          + "Decoded payload is untrusted plain text; no interpretation or decompression. "
          + "All responses, including errors, are no-store.")
  @APIResponses({
      @APIResponse(responseCode = "200", description = "Window-only diagnostic result; never full history",
          content = @Content(schema = @Schema(implementation = ReResponse.class))),
      @APIResponse(responseCode = "400", description = "Invalid explicit UTC window, body, query or cursor",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "404", description = "Attempt not found",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "410", description = "Attempt expired (if its projection row is still present)",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "429", description = "Rate, concurrency or consultation admission limit",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "502", description = "ADX/schema/ordering or correlation failure",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "503", description = "Disabled or missing/unverified deployment configuration",
          content = @Content(schema = @Schema(implementation = ErrorDto.class))),
      @APIResponse(responseCode = "504", description = "RE query timeout or cancellation",
          content = @Content(schema = @Schema(implementation = ErrorDto.class)))
  })
  public ReResponse read(@PathParam("attemptId") String attemptId,
      @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ReRequest.class)))
      JsonNode body, @Context UriInfo uri) {
    if (!uri.getQueryParameters().isEmpty() || body == null || !body.isObject()) {
      throw invalid();
    }
    var names = body.fieldNames();
    while (names.hasNext()) {
      if (!Set.of("from", "to", "cursor", "scope").contains(names.next())) {
        throw invalid();
      }
    }
    if (!body.path("from").isTextual() || !body.path("to").isTextual()
        || (body.has("cursor") && !body.get("cursor").isNull() && !body.get("cursor").isTextual())
        || (body.has("scope") && !body.get("scope").isNull() && !body.get("scope").isTextual())) {
      throw invalid();
    }
    return diagnostics.read(callers.resolve(), attemptId, new ReRequest(body.get("from").textValue(),
        body.get("to").textValue(), body.hasNonNull("cursor") ? body.get("cursor").textValue() : null,
        body.hasNonNull("scope") ? body.get("scope").textValue() : null));
  }

  private static ObservabilityException invalid() {
    return new ObservabilityException(400, "RE_INVALID_REQUEST",
        "Richiesti solo from, to ed eventuali cursor e scope nel corpo JSON.", null, null);
  }
}
