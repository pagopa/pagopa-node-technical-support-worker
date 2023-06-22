package it.gov.pagopa.nodetsworker.exceptions;

import it.gov.pagopa.nodetsworker.models.ProblemJson;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ErrorHandler implements ExceptionMapper<AppException>{

    @Override
    public Response toResponse(AppException appException) {
        var errorResponse = ProblemJson.builder()
        .status(appException.getHttpStatus())
        .title(appException.getTitle())
        .details(appException.getMessage())
        .build();
        return Response.status(appException.httpStatus).entity(errorResponse).build();
    }
}
