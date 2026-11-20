package it.gov.pagopa.nodetsworker.observability.exception;

import it.gov.pagopa.nodetsworker.observability.dto.ErrorDto;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Maps unsupported source states to an explicit upstream-data error. */
@Provider
public class OnlineSourceStateExceptionMapper implements ExceptionMapper<OnlineSourceStateException> {

  @Override
  public Response toResponse(OnlineSourceStateException exception) {
    return Response.status(Response.Status.BAD_GATEWAY)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .header("Cache-Control", "no-store")
        .entity(ErrorDto.builder()
            .code("UNSUPPORTED_ONLINE_STATE")
            .message(exception.getMessage())
            .build())
        .build();
  }
}
