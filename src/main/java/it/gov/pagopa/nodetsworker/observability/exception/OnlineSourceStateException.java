package it.gov.pagopa.nodetsworker.observability.exception;

/** Signals a Nodo Online state that is not part of the observability projection contract. */
public class OnlineSourceStateException extends RuntimeException {

  public OnlineSourceStateException(String entityType) {
    super("Nodo Online returned an unsupported " + entityType + " state.");
  }
}
