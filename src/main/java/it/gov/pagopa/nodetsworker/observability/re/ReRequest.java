package it.gov.pagopa.nodetsworker.observability.re;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Explicit UTC half-open interval. No correlation keys are accepted from the client. */
@Schema(name = "ReRequest", requiredProperties = {"from", "to"})
public record ReRequest(
    @Schema(format = "date-time", description = "Inclusive UTC instant ending in Z")
    String from,
    @Schema(format = "date-time", description = "Exclusive UTC instant; from < to <= from + 24h")
    String to,
    @Schema(nullable = true, maxLength = 76, description = "Opaque signed seek cursor; repeat the original window")
    String cursor,
    @Schema(nullable = true, enumeration = {"ATTEMPT", "POSITION"},
        description = "ATTEMPT (default): PA + NAV + token of the attempt. POSITION: PA + NAV only, so events "
            + "without a paymentToken (verifyPaymentNotice) are included, together with those of every token.")
    String scope) {
  public static final String ATTEMPT = "ATTEMPT";
  public static final String POSITION = "POSITION";

  public ReRequest(String from, String to, String cursor) {
    this(from, to, cursor, null);
  }

  /** Unknown scopes are rejected by {@link ReDiagnostics}; absent means the attempt. */
  public boolean positionScope() {
    return POSITION.equals(scope);
  }

  @Override
  public String toString() {
    return "ReRequest[redacted]";
  }
}
