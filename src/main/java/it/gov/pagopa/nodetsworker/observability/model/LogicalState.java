package it.gov.pagopa.nodetsworker.observability.model;

import java.util.List;

/** Logical payment states, in FSM order (mocks/model.mjs LOGICAL_STATES). */
public enum LogicalState {
  INSERTED,
  PAYING,
  PAID,
  NOTICE_STORED,
  NOTIFIED;

  public static final List<LogicalState> ORDERED = List.of(values());

  public static boolean isValid(String value) {
    for (LogicalState s : values()) {
      if (s.name().equals(value)) {
        return true;
      }
    }
    return false;
  }
}
