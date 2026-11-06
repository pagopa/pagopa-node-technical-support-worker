package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.domain.DomainAttempt;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.DomainRecipient;
import it.gov.pagopa.nodetsworker.observability.domain.ProjectionLogic;
import it.gov.pagopa.nodetsworker.observability.domain.TimelineItem;
import it.gov.pagopa.nodetsworker.observability.dto.AttemptDto;
import it.gov.pagopa.nodetsworker.observability.dto.PaymentDetailDto;
import it.gov.pagopa.nodetsworker.observability.dto.PaymentSummaryDto;
import it.gov.pagopa.nodetsworker.observability.dto.RecipientDto;
import it.gov.pagopa.nodetsworker.observability.dto.TimelineItemDto;
import it.gov.pagopa.nodetsworker.observability.repository.models.AttemptEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.AttemptTransitionEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.LogicalTransitionEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PaymentEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.RecipientEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.RecipientTransitionEntity;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import java.util.ArrayList;
import java.util.List;

/** Conversions between projection entities, the in-memory domain graph and the API DTOs. */
public final class ProjectionMapper {

  /** Masked token placeholder returned by every ordinary response. */
  public static final String TOKEN_DISPLAY = "••••••••";

  private ProjectionMapper() {
    throw new IllegalStateException("Utility class");
  }

  // --- entity -> domain -----------------------------------------------------

  public static DomainPayment toDomain(PaymentEntity entity) {
    List<TimelineItem> timeline = new ArrayList<>();
    for (LogicalTransitionEntity t : entity.getTimeline()) {
      timeline.add(new TimelineItem(t.getState(), t.getTransitionAt(), t.getTimestampBasis(), t.getTrigger()));
    }
    List<DomainAttempt> attempts = new ArrayList<>();
    for (AttemptEntity a : entity.getAttempts()) {
      List<TimelineItem> at = new ArrayList<>();
      for (AttemptTransitionEntity t : a.getTimeline()) {
        at.add(new TimelineItem(t.getState(), t.getTransitionAt(), t.getTimestampBasis(), t.getTrigger()));
      }
      attempts.add(DomainAttempt.builder()
          .attemptId(a.getAttemptId())
          .paymentToken(a.getPaymentToken())
          .serviceIdentifier(a.getServiceIdentifier())
          .psp(a.getPsp())
          .stazione(a.getStazione())
          .canale(a.getCanale())
          .attemptStartedAt(a.getAttemptStartedAt())
          .startTimestampBasis(a.getStartTimestampBasis())
          .firstSeenAt(a.getFirstSeenAt())
          .expiresAt(a.getExpiresAt())
          .timelineIncomplete(a.isTimelineIncomplete())
          .timeline(at)
          .build());
    }
    List<DomainRecipient> recipients = new ArrayList<>();
    for (RecipientEntity r : entity.getRecipients()) {
      List<TimelineItem> rt = new ArrayList<>();
      for (RecipientTransitionEntity t : r.getTimeline()) {
        rt.add(new TimelineItem(t.getState(), t.getTransitionAt(), t.getTimestampBasis(), t.getTrigger()));
      }
      recipients.add(DomainRecipient.builder()
          .recipientId(r.getRecipientId())
          .stazioneRecipient(r.getStazioneRecipient())
          .firstSeenAt(r.getFirstSeenAt())
          .expiresAt(r.getExpiresAt())
          .timelineIncomplete(r.isTimelineIncomplete())
          .timeline(rt)
          .build());
    }
    return DomainPayment.builder()
        .paFiscalCode(entity.getPaFiscalCode())
        .noticeNumber(entity.getNoticeNumber())
        .projectionRevision(entity.getProjectionRevision())
        .timelineIncomplete(entity.isTimelineIncomplete())
        .timeline(timeline)
        .attempts(attempts)
        .recipients(recipients)
        .build();
  }

  // --- domain -> entity (used by seeding / projection writes) ---------------

  public static PaymentEntity toEntity(DomainPayment payment) {
    PaymentEntity entity = PaymentEntity.builder()
        .paFiscalCode(payment.getPaFiscalCode())
        .noticeNumber(payment.getNoticeNumber())
        .projectionRevision(payment.getProjectionRevision())
        .timelineIncomplete(payment.isTimelineIncomplete())
        .currentState(payment.currentState())
        .timeline(new ArrayList<>())
        .attempts(new ArrayList<>())
        .build();
    for (TimelineItem t : payment.getTimeline()) {
      entity.getTimeline().add(LogicalTransitionEntity.builder()
          .payment(entity).state(t.getState()).transitionAt(t.getTransitionAt())
          .timestampBasis(t.getTimestampBasis()).trigger(t.getTrigger()).build());
    }
    for (DomainAttempt a : payment.getAttempts()) {
      AttemptEntity ae = AttemptEntity.builder()
          .payment(entity)
          .attemptId(a.getAttemptId())
          .paymentToken(a.getPaymentToken())
          .serviceIdentifier(a.getServiceIdentifier())
          .psp(a.getPsp())
          .stazione(a.getStazione())
          .canale(a.getCanale())
          .attemptStartedAt(a.getAttemptStartedAt())
          .startTimestampBasis(a.getStartTimestampBasis())
          .firstSeenAt(a.getFirstSeenAt())
          .expiresAt(a.getExpiresAt())
          .timelineIncomplete(a.isTimelineIncomplete())
          .currentState(a.currentState())
          .timeline(new ArrayList<>())
          .build();
      for (TimelineItem t : a.getTimeline()) {
        ae.getTimeline().add(AttemptTransitionEntity.builder()
            .attempt(ae).state(t.getState()).transitionAt(t.getTransitionAt())
            .timestampBasis(t.getTimestampBasis()).trigger(t.getTrigger()).build());
      }
      entity.getAttempts().add(ae);
    }
    for (DomainRecipient r : payment.getRecipients()) {
      RecipientEntity re = RecipientEntity.builder()
          .payment(entity)
          .recipientId(r.getRecipientId())
          .stazioneRecipient(r.getStazioneRecipient())
          .firstSeenAt(r.getFirstSeenAt())
          .expiresAt(r.getExpiresAt())
          .timelineIncomplete(r.isTimelineIncomplete())
          .currentState(r.currentState())
          .timeline(new ArrayList<>())
          .build();
      for (TimelineItem t : r.getTimeline()) {
        re.getTimeline().add(RecipientTransitionEntity.builder()
            .recipient(re).state(t.getState()).transitionAt(t.getTransitionAt())
            .timestampBasis(t.getTimestampBasis()).trigger(t.getTrigger()).build());
      }
      entity.getRecipients().add(re);
    }
    return entity;
  }

  // --- domain -> DTO --------------------------------------------------------

  public static TimelineItemDto toTimelineDto(TimelineItem item) {
    return TimelineItemDto.builder()
        .state(item.getState())
        .transitionAt(IsoTime.format(item.getTransitionAt()))
        .timestampBasis(item.getTimestampBasis())
        .trigger(item.getTrigger())
        .build();
  }

  private static List<TimelineItemDto> toTimelineDtos(List<TimelineItem> timeline) {
    List<TimelineItemDto> out = new ArrayList<>(timeline.size());
    for (TimelineItem t : timeline) {
      out.add(toTimelineDto(t));
    }
    return out;
  }

  /** The safe attempt projection: no raw token, masked {@code tokenDisplay}, derived flags. */
  public static AttemptDto toSafeAttemptDto(DomainAttempt attempt, DomainPayment payment) {
    return AttemptDto.builder()
        .attemptId(attempt.getAttemptId())
        .serviceIdentifier(attempt.getServiceIdentifier())
        .psp(attempt.getPsp())
        .stazione(attempt.getStazione())
        .canale(attempt.getCanale())
        .attemptStartedAt(IsoTime.format(attempt.getAttemptStartedAt()))
        .startTimestampBasis(attempt.getStartTimestampBasis())
        .timelineIncomplete(attempt.isTimelineIncomplete())
        .timeline(toTimelineDtos(attempt.getTimeline()))
        .paFiscalCode(payment.getPaFiscalCode())
        .noticeNumber(payment.getNoticeNumber())
        .state(attempt.currentState())
        .warning(ProjectionLogic.warning(attempt))
        .hasAnomaly(ProjectionLogic.anomalousAttempt(attempt))
        .tokenDisplay(TOKEN_DISPLAY)
        .projectionRevision(payment.getProjectionRevision())
        .build();
  }

  /** The recipient projection DTO: own 4-state timeline, no token, no warning. */
  public static RecipientDto toRecipientDto(DomainRecipient recipient, DomainPayment payment) {
    return RecipientDto.builder()
        .recipientId(recipient.getRecipientId())
        .stazioneRecipient(recipient.getStazioneRecipient())
        .paFiscalCode(payment.getPaFiscalCode())
        .noticeNumber(payment.getNoticeNumber())
        .state(recipient.currentState())
        .timelineIncomplete(recipient.isTimelineIncomplete())
        .hasAnomaly(ProjectionLogic.anomalousRecipient(recipient))
        .timeline(toTimelineDtos(recipient.getTimeline()))
        .projectionRevision(payment.getProjectionRevision())
        .build();
  }

  public static PaymentSummaryDto toSummaryDto(DomainPayment payment) {
    return PaymentSummaryDto.builder()
        .paFiscalCode(payment.getPaFiscalCode())
        .noticeNumber(payment.getNoticeNumber())
        .state(payment.currentState())
        .projectionRevision(payment.getProjectionRevision())
        .timelineIncomplete(payment.isTimelineIncomplete())
        .hasAnomaly(ProjectionLogic.anomalousPayment(payment))
        .warning(payment.getAttempts().stream().anyMatch(ProjectionLogic::warning))
        .attemptCount(payment.getAttempts().size())
        .build();
  }

  public static PaymentDetailDto toDetailDto(DomainPayment payment) {
    PaymentDetailDto dto = new PaymentDetailDto();
    PaymentSummaryDto summary = toSummaryDto(payment);
    dto.setPaFiscalCode(summary.getPaFiscalCode());
    dto.setNoticeNumber(summary.getNoticeNumber());
    dto.setState(summary.getState());
    dto.setProjectionRevision(summary.getProjectionRevision());
    dto.setTimelineIncomplete(summary.isTimelineIncomplete());
    dto.setHasAnomaly(summary.isHasAnomaly());
    dto.setWarning(summary.isWarning());
    dto.setAttemptCount(summary.getAttemptCount());
    dto.setTimeline(toTimelineDtos(payment.getTimeline()));
    List<AttemptDto> attempts = new ArrayList<>();
    for (DomainAttempt a : payment.getAttempts()) {
      attempts.add(toSafeAttemptDto(a, payment));
    }
    dto.setAttempts(attempts);
    List<RecipientDto> recipients = new ArrayList<>();
    for (DomainRecipient r : payment.getRecipients()) {
      recipients.add(toRecipientDto(r, payment));
    }
    dto.setRecipients(recipients);
    return dto;
  }
}
