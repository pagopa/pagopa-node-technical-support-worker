package it.gov.pagopa.nodetsworker.observability.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Contract schema {@code PaymentDetail}: a logical payment with both timelines and all safe attempts. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(requiredProperties = {"paFiscalCode", "noticeNumber", "state", "projectionRevision",
    "timelineIncomplete", "hasAnomaly", "warning", "attemptCount", "timeline", "attempts", "recipients"},
    additionalProperties = Schema.False.class)
@JsonPropertyOrder({"paFiscalCode", "noticeNumber", "state", "projectionRevision", "timelineIncomplete",
    "hasAnomaly", "warning", "attemptCount", "timeline", "attempts", "recipients"})
public class PaymentDetailDto extends PaymentSummaryDto {
  private List<TimelineItemDto> timeline;
  private List<AttemptDto> attempts;
  private List<RecipientDto> recipients;
}
