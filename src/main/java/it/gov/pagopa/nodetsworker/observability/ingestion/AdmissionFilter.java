package it.gov.pagopa.nodetsworker.observability.ingestion;

/**
 * The admission filter of the RE consumer (spec §4.1). A message is processed only when
 * {@code esito = CAMBIO_STATO} and {@code status} starts with {@code payment_} or {@code position_}.
 * Everything else is dropped without being persisted or recorded in the inbox: interface events
 * ({@code categoriaEvento = INTERFACCIA}) and prefix-less states (RPT / cart). The prefix selects
 * the entity the state belongs to: {@code payment_} the attempt, {@code position_} the logical
 * position, and {@code receipt_recipient_} the receipt recipient (a third entity keyed by the
 * logical pair and the {@code stazione_recipient} carried in {@code info}). Receipt-recipient events
 * are projected, not dropped (overrides the earlier spec §7.1 / AC-06 exclusion).
 */
public final class AdmissionFilter {

  public static final String CAMBIO_STATO = "CAMBIO_STATO";
  public static final String INTERFACCIA = "INTERFACCIA";
  public static final String PAYMENT_PREFIX = "payment_";
  public static final String POSITION_PREFIX = "position_";
  public static final String RECIPIENT_PREFIX = "receipt_recipient_";

  private AdmissionFilter() {
    throw new IllegalStateException("Utility class");
  }

  /** The entity a prefixed state belongs to. */
  public enum Entity {
    PAYMENT,
    POSITION,
    RECIPIENT
  }

  /** Result of admitting a message: target entity, bare state, and optional source trigger. */
  public record Admitted(Entity entity, String state, String trigger) {
  }

  /**
   * Returns the admitted projection target, or {@code null} when the message must be dropped.
   * The returned {@code state} keeps the original value recorded by the Nodo, prefix removed (§2).
   */
  public static Admitted admit(ReMessage message) {
    if (message == null || !CAMBIO_STATO.equals(message.getEsito())) {
      return null;
    }
    if (INTERFACCIA.equals(message.getCategoriaEvento())) {
      return null;
    }
    String status = message.getStatus();
    if (status == null) {
      return null;
    }
    // Check the recipient prefix before the payment one: "receipt_recipient_" is distinct, but
    // keeping it first makes the entity selection explicit and order-independent.
    if (status.startsWith(RECIPIENT_PREFIX)) {
      String bare = status.substring(RECIPIENT_PREFIX.length());
      return new Admitted(Entity.RECIPIENT, bare, message.getTipoEvento());
    }
    if (status.startsWith(PAYMENT_PREFIX)) {
      String bare = status.substring(PAYMENT_PREFIX.length());
      return new Admitted(Entity.PAYMENT, bare, message.getTipoEvento());
    }
    if (status.startsWith(POSITION_PREFIX)) {
      String bare = status.substring(POSITION_PREFIX.length());
      return new Admitted(Entity.POSITION, bare, message.getTipoEvento());
    }
    return null;
  }
}
