package it.gov.pagopa.nodetsworker.observability.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Renders {@link ObservabilityException} as the contract JSON error body with its HTTP status. */
@Provider
public class ObservabilityExceptionMapper implements ExceptionMapper<ObservabilityException> {

  @Override
  public Response toResponse(ObservabilityException exception) {
    return Response.status(exception.getStatus())
        .type("application/json; charset=utf-8")
        .header("Cache-Control", "no-store")
        .entity(exception.toBody())
        .build();
  }
}
