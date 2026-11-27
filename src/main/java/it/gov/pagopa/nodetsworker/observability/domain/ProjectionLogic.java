package it.gov.pagopa.nodetsworker.observability.domain;

import it.gov.pagopa.nodetsworker.observability.model.AttemptState;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure derivations over the projection, a faithful port of the helpers in {@code mocks/model.mjs}.
 * All methods are read-only and never mutate the domain graph.
 */
public final class ProjectionLogic {

  private ProjectionLogic() {
    throw new IllegalStateException("Utility class");
  }

  public static boolean warning(DomainAttempt attempt) {
    return AttemptState.WARNING.contains(attempt.currentState());
  }

  public static boolean anomalous(List<TimelineItem> timeline) {
    return timeline.stream().anyMatch(t -> t.getTimestampBasis() == TimestampBasis.ANOMALY);
  }

  public static boolean anomalousAttempt(DomainAttempt attempt) {
    return anomalous(attempt.getTimeline());
  }

  public static boolean anomalousRecipient(DomainRecipient recipient) {
    return anomalous(recipient.getTimeline());
  }

  public static boolean anomalousPayment(DomainPayment payment) {
    return anomalous(payment.getTimeline())
        || payment.getAttempts().stream().anyMatch(ProjectionLogic::anomalousAttempt)
        || payment.getRecipients().stream().anyMatch(ProjectionLogic::anomalousRecipient);
  }

  private static boolean inWindow(Instant value, Instant from, Instant to, boolean live) {
    return value != null && !value.isBefore(from) && (live || value.isBefore(to));
  }

  private static boolean hasActivity(List<TimelineItem> timeline, Instant from, Instant to, boolean live) {
    return timeline.stream().anyMatch(t -> t.getTimestampBasis() != TimestampBasis.ANOMALY
        && inWindow(t.getTransitionAt(), from, to, live));
  }

  /**
   * Restrict the projection to the entities that had activity in {@code [from, to)} (open on the right
   * when {@code live}): at least one dated transition, or for an attempt its start. A payment is kept when
   * it, or one of its attempts or recipients, was active, and carries only the active attempts and
   * recipients; the shown state is always the current one. Entities with no dated transition (ANOMALY
   * only) belong to no interval. Mirrors {@code withinWindow()} in {@code mocks/model.mjs}; read-only views.
   */
  public static List<DomainPayment> withinWindow(List<DomainPayment> payments, Instant from, Instant to,
      boolean live) {
    List<DomainPayment> result = new ArrayList<>();
    for (DomainPayment payment : payments) {
      List<DomainAttempt> attempts = new ArrayList<>();
      for (DomainAttempt a : payment.getAttempts()) {
        if (inWindow(a.getAttemptStartedAt(), from, to, live) || hasActivity(a.getTimeline(), from, to, live)) {
          attempts.add(a);
        }
      }
      List<DomainRecipient> recipients = new ArrayList<>();
      for (DomainRecipient r : payment.getRecipients()) {
        if (hasActivity(r.getTimeline(), from, to, live)) {
          recipients.add(r);
        }
      }
      if (!attempts.isEmpty() || !recipients.isEmpty()
          || hasActivity(payment.getTimeline(), from, to, live)) {
        result.add(payment.withAttemptsAndRecipients(attempts, recipients));
      }
    }
    return result;
  }

  private static boolean match(String value, String wanted) {
    if (wanted == null) {
      return true;
    }
    if (Filters.UNKNOWN.equals(wanted)) {
      return value == null;
    }
    return Objects.equals(value, wanted);
  }

  /**
   * Select the payments matching the filters, keeping only matching attempts and dropping payments
   * left with no matching attempt when an attempt-level filter is active. Without such a filter,
   * logical/recipient-only observations remain visible. Returns read-only shallow views.
   */
  public static List<DomainPayment> selected(List<DomainPayment> payments, Filters filters, String allowedPa) {
    List<DomainPayment> result = new ArrayList<>();
    for (DomainPayment payment : payments) {
      if (allowedPa != null && !allowedPa.equals(payment.getPaFiscalCode())) {
        continue;
      }
      if (!match(payment.getPaFiscalCode(), filters.getPaFiscalCode())
          || !match(payment.getNoticeNumber(), filters.getNoticeNumber())
          || !match(payment.currentState(), filters.getLogicalState())) {
        continue;
      }
      if (filters.wantsAnomaliesOnly() && !anomalousPayment(payment)) {
        continue;
      }
      List<DomainAttempt> attempts = new ArrayList<>();
      for (DomainAttempt a : payment.getAttempts()) {
        if (match(a.currentState(), filters.getAttemptState())
            && match(a.getServiceIdentifier(), filters.getServiceIdentifier())
            && match(a.getPsp(), filters.getPsp())
            && match(a.getCanale(), filters.getCanale())
            && match(a.getStazione(), filters.getStazione())
            && (!filters.wantsUnresolvedOnly() || warning(a))) {
          attempts.add(a);
        }
      }
      boolean requiresAttempt = filters.getAttemptState() != null || filters.getServiceIdentifier() != null
          || filters.getPsp() != null || filters.getCanale() != null || filters.getStazione() != null
          || filters.wantsUnresolvedOnly();
      if (!attempts.isEmpty() || !requiresAttempt) {
        result.add(payment.withAttempts(attempts));
      }
    }
    return result;
  }
}
