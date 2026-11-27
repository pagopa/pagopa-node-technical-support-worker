package it.gov.pagopa.nodetsworker.observability.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response of the dedicated payment-info API ({@code /payments/...}): the same view as
 * {@link PaymentDetailDto}, with attempts carrying the raw payment token ({@link AttemptInfoDto}).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(requiredProperties = {"paFiscalCode", "noticeNumber", "state", "projectionRevision",
    "timelineIncomplete", "hasAnomaly", "warning", "attemptCount", "timeline", "attempts", "recipients"},
    additionalProperties = Schema.False.class)
@JsonPropertyOrder({"paFiscalCode", "noticeNumber", "state", "projectionRevision", "timelineIncomplete",
    "hasAnomaly", "warning", "attemptCount", "timeline", "attempts", "recipients"})
public class PaymentInfoDto extends PaymentSummaryDto {
  private List<TimelineItemDto> timeline;
  private List<AttemptInfoDto> attempts;
  private List<RecipientDto> recipients;
}
