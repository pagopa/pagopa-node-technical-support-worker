package it.gov.pagopa.nodetsworker.observability.exception;

import it.gov.pagopa.nodetsworker.observability.dto.ErrorDto;

/** A functional error carrying the HTTP status and the contract error body. */
public class ObservabilityException extends RuntimeException {

  private final transient int status;
  private final transient String code;
  private final transient Boolean resync;
  private final transient String fallback;

  public ObservabilityException(int status, String code, String message, Boolean resync, String fallback) {
    super(message);
    this.status = status;
    this.code = code;
    this.resync = resync;
    this.fallback = fallback;
  }

  public int getStatus() {
    return status;
  }

  public ErrorDto toBody() {
    return ErrorDto.builder().code(code).message(getMessage()).resync(resync).fallback(fallback).build();
  }
}
