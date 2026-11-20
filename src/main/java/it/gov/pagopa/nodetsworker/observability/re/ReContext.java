package it.gov.pagopa.nodetsworker.observability.re;

import java.time.Instant;

/** Captured projection identity; it must never be logged or persisted by diagnostics. */
public record ReContext(String attemptId, String pa, String notice, String token, Instant expiresAt) {
  @Override
  public String toString() {
    return "ReContext[redacted]";
  }
}
