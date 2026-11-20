package it.gov.pagopa.nodetsworker.observability.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A receipt recipient of a logical payment, identified by the {@code stazione_recipient}
 * discriminator (null = a single unidentified recipient). {@code recipientId} is a stable technical
 * id; {@code firstSeenAt}/{@code expiresAt} are retention metadata, never exposed as functional data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainRecipient {
  private String recipientId;
  private String stazioneRecipient;
  private Instant firstSeenAt;
  private Instant expiresAt;
  private boolean timelineIncomplete;
  @Builder.Default private List<TimelineItem> timeline = new ArrayList<>();

  public String currentState() {
    return timeline.isEmpty() ? null : timeline.get(timeline.size() - 1).getState();
  }
}
