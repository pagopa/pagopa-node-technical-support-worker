package it.gov.pagopa.nodetsworker.observability.model;

import java.util.List;

/** Attempt states, in the order declared by the functional contract (mocks/model.mjs ATTEMPT_STATES). */
public enum AttemptState {
  PAYING,
  PAYING_RPT,
  PAID_NORPT,
  PAID,
  FAILED_NORPT,
  FAILED,
  CANCELLED,
  CANCELLED_NORPT,
  NOTICE_GENERATED,
  NOTICE_STORED,
  NOTICE_SENT,
  NOTIFIED,
  PAYMENT_RESERVED,
  PAYMENT_SENT,
  PAYMENT_ACCEPTED,
  PAYMENT_REFUSED,
  PAYMENT_UNKNOWN,
  PAYMENT_SEND_ERROR;

  public static final List<AttemptState> ORDERED = List.of(values());

  /** States that keep an attempt flagged as an unresolved warning. */
  public static final List<String> WARNING = List.of(PAYMENT_UNKNOWN.name(), PAYMENT_SEND_ERROR.name());

  public static boolean isValid(String value) {
    for (AttemptState s : values()) {
      if (s.name().equals(value)) {
        return true;
      }
    }
    return false;
  }
}
