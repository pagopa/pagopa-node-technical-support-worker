package it.gov.pagopa.nodetsworker.observability.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A logical payment identified by {@code (paFiscalCode, noticeNumber)} with its attempts and timeline. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainPayment {
  private String paFiscalCode;
  private String noticeNumber;
  private long projectionRevision;
  private boolean timelineIncomplete;
  @Builder.Default private List<TimelineItem> timeline = new ArrayList<>();
  @Builder.Default private List<DomainAttempt> attempts = new ArrayList<>();
  @Builder.Default private List<DomainRecipient> recipients = new ArrayList<>();

  public String currentState() {
    return timeline.isEmpty() ? null : timeline.get(timeline.size() - 1).getState();
  }

  public String key() {
    return paFiscalCode + "/" + noticeNumber;
  }

  /**
   * A shallow view with a filtered attempt list, reusing the same attempt instances (read-only use).
   * Recipients attach to the logical pair, not to the attempt, so they are carried through unfiltered.
   */
  public DomainPayment withAttempts(List<DomainAttempt> filtered) {
    return new DomainPayment(paFiscalCode, noticeNumber, projectionRevision, timelineIncomplete,
        timeline, filtered, recipients);
  }

  /** A shallow view with filtered attempts and recipients, reusing the same instances (read-only use). */
  public DomainPayment withAttemptsAndRecipients(List<DomainAttempt> filteredAttempts,
      List<DomainRecipient> filteredRecipients) {
    return new DomainPayment(paFiscalCode, noticeNumber, projectionRevision, timelineIncomplete,
        timeline, filteredAttempts, filteredRecipients);
  }
}
