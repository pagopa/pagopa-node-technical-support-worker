package it.gov.pagopa.nodetsworker.observability.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Attempt of the dedicated payment-info API ({@code /payments/...}): the safe {@link AttemptDto} plus the
 * raw payment token. Never used by the observability API or the SSE outbox, which expose only
 * {@code tokenDisplay} (the raw token is revealed there through the audited reveal endpoint).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(requiredProperties = {"attemptId", "serviceIdentifier", "psp", "stazione", "canale",
    "attemptStartedAt", "startTimestampBasis", "timelineIncomplete", "timeline", "paFiscalCode",
    "noticeNumber", "state", "warning", "hasAnomaly", "tokenDisplay", "paymentToken", "projectionRevision"},
    additionalProperties = Schema.False.class)
@JsonPropertyOrder({"attemptId", "serviceIdentifier", "psp", "stazione", "canale", "attemptStartedAt",
    "startTimestampBasis", "timelineIncomplete", "timeline", "paFiscalCode", "noticeNumber",
    "state", "warning", "hasAnomaly", "tokenDisplay", "paymentToken", "projectionRevision"})
public class AttemptInfoDto extends AttemptDto {
  @Schema(nullable = true)
  private String paymentToken;

  public AttemptInfoDto(AttemptDto safe, String paymentToken) {
    setAttemptId(safe.getAttemptId());
    setServiceIdentifier(safe.getServiceIdentifier());
    setPsp(safe.getPsp());
    setStazione(safe.getStazione());
    setCanale(safe.getCanale());
    setAttemptStartedAt(safe.getAttemptStartedAt());
    setStartTimestampBasis(safe.getStartTimestampBasis());
    setTimelineIncomplete(safe.isTimelineIncomplete());
    setTimeline(safe.getTimeline());
    setPaFiscalCode(safe.getPaFiscalCode());
    setNoticeNumber(safe.getNoticeNumber());
    setState(safe.getState());
    setWarning(safe.isWarning());
    setHasAnomaly(safe.isHasAnomaly());
    setTokenDisplay(safe.getTokenDisplay());
    setProjectionRevision(safe.getProjectionRevision());
    this.paymentToken = paymentToken;
  }
}
