package it.gov.pagopa.nodetsworker.resources.exceptionmapper;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageInterface;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.ProblemJson;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.UnexpectedTypeException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ExceptionMappers {

  @Inject Logger log;

  @ServerExceptionMapper
  public Response mapWebApplicationException(WebApplicationException webApplicationException) {
    if (webApplicationException.getCause() instanceof JsonMappingException jsonMappingException) {
      return mapJsonMappingException(jsonMappingException).toResponse();
    } else if (webApplicationException.getCause()
        instanceof JsonParseException jsonParseException) {
      return mapJsonParseException(jsonParseException).toResponse();
    }

    return webApplicationException.getResponse();
  }

  @ServerExceptionMapper
  public RestResponse<ProblemJson> mapAppException(AppException appEx) {
    AppErrorCodeMessageInterface codeMessage = appEx.getCodeMessage();
    RestResponse.Status status = codeMessage.httpStatus();
    String message = codeMessage.message(appEx.getArgs());

    ProblemJson errorResponse =
        ProblemJson.builder()
            .status(status.getStatusCode())
            .details(codeMessage.message(appEx.getArgs()))
            .title(codeMessage.errorCode())
            .build();

    return RestResponse.status(codeMessage.httpStatus(), errorResponse);
  }

  private RestResponse<ProblemJson> mapJsonMappingException(
      JsonMappingException jsonMappingException) {
    // quando jackson riesce a parsare il messaggio perchè non formato json valido
    AppException appEx =
        new AppException(
            jsonMappingException, AppErrorCodeMessageEnum.BAD_REQUEST_INPUT_JSON_NON_VALID_FORMAT);

    AppErrorCodeMessageInterface codeMessage = appEx.getCodeMessage();
    RestResponse.Status status = codeMessage.httpStatus();

    ProblemJson errorResponse =
        ProblemJson.builder()
            .status(status.getStatusCode())
            .details(codeMessage.message(appEx.getArgs()))
            .title(codeMessage.errorCode())
            .build();

    return RestResponse.status(codeMessage.httpStatus(), errorResponse);
  }

  private RestResponse<ProblemJson> mapJsonParseException(JsonParseException jsonParseException) {
    // quando jackson riesce a parsare il messaggio perchè non formato json valido

    AppException appEx =
        new AppException(
            jsonParseException, AppErrorCodeMessageEnum.BAD_REQUEST_INPUT_JSON_NON_VALID_FORMAT);

    AppErrorCodeMessageInterface codeMessage = appEx.getCodeMessage();
    RestResponse.Status status = codeMessage.httpStatus();

    ProblemJson errorResponse =
        ProblemJson.builder()
            .status(status.getStatusCode())
            .details(codeMessage.message(appEx.getArgs()))
            .title(codeMessage.errorCode())
            .build();

    return RestResponse.status(codeMessage.httpStatus(), errorResponse);
  }

  @SuppressWarnings("unchecked")
  @ServerExceptionMapper
  public RestResponse<ProblemJson> mapInvalidFormatException(
      InvalidFormatException invalidFormatException) {
    // quando jackson riesce a parsare il messaggio per popolare il bean ma i valori NON sono
    // corretti
    String field =
        invalidFormatException.getPath().stream()
            .map(Reference::getFieldName)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("."));
    String currentValue = invalidFormatException.getValue().toString();
    AppException appEx = null;
    try {
      Class<?> target = Class.forName(invalidFormatException.getTargetType().getName());
      if (target.isEnum()) {
        Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) target;
        List<String> accepted = Stream.of(enumClass.getEnumConstants()).map(Enum::name).toList();
        appEx =
            new AppException(
                invalidFormatException,
                AppErrorCodeMessageEnum.BAD_REQUEST_INPUT_JSON_ENUM,
                field,
                currentValue,
                accepted);
      } else if (target.isAssignableFrom(Instant.class)) {
        appEx =
            new AppException(
                invalidFormatException,
                AppErrorCodeMessageEnum.BAD_REQUEST_INPUT_JSON_INSTANT,
                field,
                currentValue);
      } else {
        appEx =
            new AppException(
                invalidFormatException,
                AppErrorCodeMessageEnum.BAD_REQUEST_INPUT_JSON,
                field,
                currentValue);
      }

    } catch (ClassNotFoundException e) {
      appEx =
          new AppException(
              invalidFormatException,
              AppErrorCodeMessageEnum.BAD_REQUEST_INPUT_JSON,
              field,
              currentValue);
    }

    AppErrorCodeMessageInterface codeMessage = appEx.getCodeMessage();
    RestResponse.Status status = codeMessage.httpStatus();

    ProblemJson errorResponse =
        ProblemJson.builder()
            .status(status.getStatusCode())
            .details(codeMessage.message(appEx.getArgs()))
            .title(codeMessage.errorCode())
            .build();

    return RestResponse.status(codeMessage.httpStatus(), errorResponse);
  }

  @ServerExceptionMapper
  public RestResponse<ProblemJson> mapMismatchedInputException(
      MismatchedInputException mismatchedInputException) {
    // quando jackson NON riesce a parsare il messaggio per popolare il bean
    String field =
        mismatchedInputException.getPath().stream()
            .map(Reference::getFieldName)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("."));
    AppException appEx =
        new AppException(
            mismatchedInputException,
            AppErrorCodeMessageEnum.BAD_REQUEST_INPUT_JSON_DESERIALIZE_ERROR,
            field);

    AppErrorCodeMessageInterface codeMessage = appEx.getCodeMessage();
    RestResponse.Status status = codeMessage.httpStatus();

    ProblemJson errorResponse =
        ProblemJson.builder()
            .status(status.getStatusCode())
            .details(codeMessage.message(appEx.getArgs()))
            .title(codeMessage.errorCode())
            .build();

    return RestResponse.status(codeMessage.httpStatus(), errorResponse);
  }

  @ServerExceptionMapper
  public RestResponse<ProblemJson> mapUnexpectedTypeException(UnexpectedTypeException exception) {
    return mapThrowable(exception);
  }

  @ServerExceptionMapper
  public RestResponse<ProblemJson> mapThrowable(Throwable exception) {
    AppException appEx = new AppException(exception, AppErrorCodeMessageEnum.ERROR);
    AppErrorCodeMessageInterface codeMessage = appEx.getCodeMessage();
    RestResponse.Status status = codeMessage.httpStatus();

    ProblemJson errorResponse =
        ProblemJson.builder()
            .status(status.getStatusCode())
            .details(codeMessage.message(appEx.getArgs()))
            .title(codeMessage.errorCode())
            .build();

    return RestResponse.status(codeMessage.httpStatus(), errorResponse);
  }

  @ServerExceptionMapper
  public RestResponse<ProblemJson> mapConstraintViolationException(
      ConstraintViolationException constraintViolationException) {

    AppException appEx =
        new AppException(constraintViolationException, AppErrorCodeMessageEnum.BAD_REQUEST);
    AppErrorCodeMessageInterface codeMessage = appEx.getCodeMessage();
    RestResponse.Status status = codeMessage.httpStatus();

    ProblemJson errorResponse =
        ProblemJson.builder()
            .status(status.getStatusCode())
            .details(codeMessage.message(appEx.getArgs()))
            .title(codeMessage.errorCode())
            .build();

    return RestResponse.status(codeMessage.httpStatus(), errorResponse);
  }
}
