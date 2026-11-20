package it.gov.pagopa.nodetsworker.observability.domain;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single payment attempt (one {@code paymentToken}) of a logical payment. {@code paymentToken},
 * {@code firstSeenAt} and {@code expiresAt} are technical metadata, never exposed by the functional API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainAttempt {
  private String attemptId;
  private String paymentToken;
  private String serviceIdentifier;
  private String psp;
  private String stazione;
  private String canale;
  private Instant attemptStartedAt;
  private TimestampBasis startTimestampBasis;
  private Instant firstSeenAt;
  private Instant expiresAt;
  private boolean timelineIncomplete;
  @Builder.Default private List<TimelineItem> timeline = new ArrayList<>();

  public String currentState() {
    return timeline.get(timeline.size() - 1).getState();
  }
}
