package it.gov.pagopa.nodetsworker.observability.domain;

import java.time.Instant;

/** Detached point-in-time projection data; the token must never be serialized or logged. */
public record DbCheckContext(
    String attemptId, String paFiscalCode, String noticeNumber, String paymentToken,
    String serviceIdentifier, String logicalState, String attemptState,
    long projectionRevision, Instant expiresAt) {

  @Override
  public String toString() {
    return "DbCheckContext[attemptId=" + attemptId + "]";
  }
}
