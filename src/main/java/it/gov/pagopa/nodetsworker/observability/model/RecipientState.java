package it.gov.pagopa.nodetsworker.observability.model;

import java.util.List;

/** Receipt recipient states, in FSM order (FSM stati di pagamento.md §3, RecipientStatusEnum). */
public enum RecipientState {
  NOTICE_GENERATED,
  NOTICE_SENT,
  NOTICE_PENDING,
  NOTIFIED;

  public static final List<RecipientState> ORDERED = List.of(values());

  public static boolean isValid(String value) {
    for (RecipientState s : values()) {
      if (s.name().equals(value)) {
        return true;
      }
    }
    return false;
  }
}
