package it.gov.pagopa.nodetsworker.observability.domain;

/**
 * Direction of a primitive call relative to the Nodo (spec §3.3.2), taken from the RE parties of its
 * {@code REQ} event: the Nodo serves the call ({@code erogatore}) when others call it, and is the caller
 * ({@code fruitore}) when it calls a PA or a PSP. It is never inferred from the primitive name.
 */
public enum PrimitiveDirection {
  /** The Nodo is the {@code erogatore}: another system calls it. */
  INBOUND,
  /** The Nodo is the {@code fruitore}: it calls a PA or a PSP. */
  OUTBOUND,
  /** Neither party is the Nodo, or the parties are absent. */
  UNKNOWN;

  /** RE identifier of the Nodo as {@code fruitore}/{@code erogatore}. */
  public static final String NODO = "NodoDeiPagamentiSPC";

  public static PrimitiveDirection of(String fruitore, String erogatore) {
    if (isNodo(erogatore)) {
      return INBOUND;
    }
    return isNodo(fruitore) ? OUTBOUND : UNKNOWN;
  }

  /** The stored or transported name, {@link #UNKNOWN} when absent or not recognised (older rows). */
  public static PrimitiveDirection parse(String value) {
    if (value != null) {
      for (PrimitiveDirection direction : values()) {
        if (direction.name().equals(value)) {
          return direction;
        }
      }
    }
    return UNKNOWN;
  }

  private static boolean isNodo(String party) {
    return party != null && NODO.equalsIgnoreCase(party.trim());
  }
}
