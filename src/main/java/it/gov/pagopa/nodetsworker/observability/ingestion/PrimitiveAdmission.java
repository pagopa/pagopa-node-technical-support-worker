package it.gov.pagopa.nodetsworker.observability.ingestion;

/**
 * Admission of RE interface events as primitive calls (spec §3.3.2). Only {@code INTERFACCIA} events
 * whose {@code sottoTipoEvento} is {@code REQ} or {@code RESP} and that carry the correlation pair
 * ({@code sessionId}, {@code tipoEvento}) are counted. They never reach the payment projection.
 */
public final class PrimitiveAdmission {

  public static final String REQ = "REQ";
  public static final String RESP = "RESP";
  private static final int MAX_KEY = 128;

  private PrimitiveAdmission() {
    throw new IllegalStateException("Utility class");
  }

  public enum Kind {
    REQUEST,
    RESPONSE
  }

  public record Admitted(Kind kind, String sessionId, String tipoEvento) {
  }

  /** The admitted primitive event, or {@code null} when the message is not a countable REQ/RESP. */
  public static Admitted admit(ReMessage message) {
    if (message == null || !AdmissionFilter.INTERFACCIA.equals(message.getCategoriaEvento())) {
      return null;
    }
    Kind kind = REQ.equals(message.getSottoTipoEvento()) ? Kind.REQUEST
        : RESP.equals(message.getSottoTipoEvento()) ? Kind.RESPONSE : null;
    if (kind == null || !usable(message.getSessionId()) || !usable(message.getTipoEvento())) {
      return null;
    }
    return new Admitted(kind, message.getSessionId(), message.getTipoEvento());
  }

  private static boolean usable(String value) {
    return value != null && !value.isBlank() && value.length() <= MAX_KEY;
  }
}
