package it.gov.pagopa.nodetsworker.observability.domain;

import java.time.Instant;

/** A primitive call left unanswered for more than the budget; dimensions are {@code null} when not available. */
public record PrimitiveWarningRow(Instant calledAt, String serviceIdentifier, String primitive, String psp,
    String canale, String paFiscalCode, String stazione) {
}
