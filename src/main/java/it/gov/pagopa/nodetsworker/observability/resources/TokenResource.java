package it.gov.pagopa.nodetsworker.observability.resources;

import it.gov.pagopa.nodetsworker.observability.dto.RevealResponseDto;
import it.gov.pagopa.nodetsworker.observability.dto.TokenLookupRequestDto;
import it.gov.pagopa.nodetsworker.observability.dto.TokenLookupResponseDto;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.service.AuditService;
import it.gov.pagopa.nodetsworker.observability.service.Caller;
import it.gov.pagopa.nodetsworker.observability.service.CallerResolver;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.util.Map;

/** Token operations: reveal a token in clear and look up an attempt by token. Both are audited. */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NoStore
public class TokenResource {

  @Inject
  ObservabilityService service;

  @Inject
  AuditService auditService;

  @Inject
  CallerResolver callerResolver;

  @POST
  @Path("/attempts/{attemptId}/reveal")
  public RevealResponseDto reveal(@PathParam("attemptId") String attemptId, Map<String, Object> body,
      @Context UriInfo uriInfo) {
    Caller caller = callerResolver.resolve();
    requireNoQuery(uriInfo);
    requireTokens(caller);
    if (body != null && !body.isEmpty()) {
      throw Errors.invalidRequest();
    }
    // 404 before audit: do not record a reveal of something that does not exist.
    RevealResponseDto response = service.reveal(caller, attemptId);
    auditService.record(caller, "TOKEN_REVEAL", response.getAttemptId());
    return response;
  }

  @POST
  @Path("/token-lookup")
  public TokenLookupResponseDto tokenLookup(TokenLookupRequestDto body, @Context UriInfo uriInfo) {
    Caller caller = callerResolver.resolve();
    requireNoQuery(uriInfo);
    requireTokens(caller);
    if (body == null || body.getPaymentToken() == null
        || body.getPaymentToken().isEmpty() || body.getPaymentToken().length() > 256) {
      throw Errors.invalidRequest();
    }
    TokenLookupResponseDto response = service.tokenLookup(caller, body.getPaymentToken(),
        emptyToNull(body.getPaFiscalCode()), emptyToNull(body.getNoticeNumber()));
    // Audit the search (hit or miss) without persisting the supplied secret, then 404 on a miss.
    auditService.record(caller, "TOKEN_LOOKUP", response.getAttemptId());
    if (response.getPaFiscalCode() == null) {
      throw Errors.notFound("Tentativo non disponibile.");
    }
    return response;
  }

  /** Treat a missing or blank optional filter as absent (no constraint), not as an empty-string value. */
  private static String emptyToNull(String value) {
    return (value == null || value.isEmpty()) ? null : value;
  }

  private static void requireTokens(Caller caller) {
    if (!caller.canReadTokens()) {
      throw Errors.forbidden("Privilegio token richiesto.");
    }
  }

  private static void requireNoQuery(UriInfo uriInfo) {
    if (!uriInfo.getQueryParameters().isEmpty()) {
      throw Errors.invalidRequest();
    }
  }
}
