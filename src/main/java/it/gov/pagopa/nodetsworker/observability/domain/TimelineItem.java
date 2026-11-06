package it.gov.pagopa.nodetsworker.observability.domain;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One accepted state transition in a logical-payment or attempt timeline. {@code transitionAt}
 * is {@code null} when the basis is {@link TimestampBasis#ANOMALY} (missing/unparseable event time).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimelineItem {
  private String state;
  private Instant transitionAt;
  private TimestampBasis timestampBasis;
  private String trigger;

  public TimelineItem(String state, Instant transitionAt, TimestampBasis timestampBasis) {
    this(state, transitionAt, timestampBasis, null);
  }

  public TimelineItem copy() {
    return new TimelineItem(state, transitionAt, timestampBasis, trigger);
  }
}
