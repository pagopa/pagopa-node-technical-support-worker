package it.gov.pagopa.nodetsworker.observability.exception;

/** Factory of the contract errors, mirroring the status/code pairs produced by {@code mocks/server.mjs}. */
public final class Errors {

  private Errors() {
    throw new IllegalStateException("Utility class");
  }

  public static ObservabilityException invalidRequest(String message) {
    return new ObservabilityException(400, "INVALID_REQUEST", message, null, null);
  }

  public static ObservabilityException invalidRequest() {
    return invalidRequest("Parametri non validi.");
  }

  public static ObservabilityException notFound(String message) {
    return new ObservabilityException(404, "NOT_FOUND", message, null, null);
  }

  public static ObservabilityException forbidden(String message) {
    return new ObservabilityException(403, "FORBIDDEN", message, null, null);
  }

  public static ObservabilityException cursorExpired() {
    return new ObservabilityException(410, "CURSOR_EXPIRED", "Ricaricare la prima pagina.", null, null);
  }

  public static ObservabilityException sseCursorExpired() {
    return new ObservabilityException(410, "SSE_CURSOR_EXPIRED",
        "Aprire un nuovo stream e riallineare lo snapshot.", true, null);
  }

  public static ObservabilityException unsupportedAggregation() {
    return new ObservabilityException(422, "UNSUPPORTED_AGGREGATION",
        "Combinazione non disponibile negli aggregati; usare la lista tentativi.", null, "/api/attempts");
  }

  public static ObservabilityException auditUnavailable() {
    return new ObservabilityException(500, "AUDIT_UNAVAILABLE", "Audit non disponibile; operazione negata.", null, null);
  }

  public static ObservabilityException maintenanceUnavailable() {
    return new ObservabilityException(503, "MAINTENANCE_UNAVAILABLE",
        "Manutenzioni programmate non disponibili.", null, null);
  }

  public static ObservabilityException internal() {
    return new ObservabilityException(500, "INTERNAL_ERROR", "Errore interno.", null, null);
  }
}
