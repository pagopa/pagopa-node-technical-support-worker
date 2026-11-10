package it.gov.pagopa.nodetsworker.observability.model;

/** Provenance of a timeline timestamp (functional contract, see mocks/model.mjs). */
public enum TimestampBasis {
  SOURCE_EVENT,
  SOURCE_SYNC,
  INFERRED,
  ANOMALY
}
