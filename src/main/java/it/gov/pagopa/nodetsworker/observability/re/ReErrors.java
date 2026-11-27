package it.gov.pagopa.nodetsworker.observability.re;

import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;

/** Never retain upstream exception causes: SDK diagnostics may include query parameters. */
final class ReErrors {
  private ReErrors() { }

  static ObservabilityException error(int status, String code, String message) {
    return new ObservabilityException(status, code, message, null, null);
  }

  static ObservabilityException invalid() {
    return error(400, "RE_INVALID_REQUEST", "Intervallo UTC o cursore RE non valido.");
  }

  static ObservabilityException config() {
    return error(503, "RE_UNAVAILABLE", "Diagnostica RE disabilitata o configurazione non verificata.");
  }

  static ObservabilityException source() {
    return error(502, "RE_SOURCE_ERROR", "Risposta RE non disponibile, ambigua o schema non valido.");
  }
}
