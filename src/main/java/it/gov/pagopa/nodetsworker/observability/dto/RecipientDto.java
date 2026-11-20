package it.gov.pagopa.nodetsworker.observability.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Contract schema {@code Recipient}: a receipt recipient of a logical payment, with its own 4-state
 * timeline. It carries no payment token (the token travels in the receipt but does not identify the
 * recipient) and no {@code warning} (warning is an attempt-only concept).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(requiredProperties = {"recipientId", "stazioneRecipient", "paFiscalCode", "noticeNumber",
    "state", "timelineIncomplete", "hasAnomaly", "timeline", "projectionRevision"},
    additionalProperties = Schema.False.class,
    description = "Receipt recipient keyed by PA/NAV/stazioneRecipient. No token association or primary role.")
@JsonPropertyOrder({"recipientId", "stazioneRecipient", "paFiscalCode", "noticeNumber", "state",
    "timelineIncomplete", "hasAnomaly", "timeline", "projectionRevision"})
public class RecipientDto {
  private String recipientId;
  @Schema(nullable = true, description = "Null denotes the single unidentified recipient of this logical pair.")
  private String stazioneRecipient;
  private String paFiscalCode;
  private String noticeNumber;
  @Schema(enumeration = {"NOTICE_GENERATED", "NOTICE_SENT", "NOTICE_PENDING", "NOTIFIED"})
  private String state;
  private boolean timelineIncomplete;
  private boolean hasAnomaly;
  private List<TimelineItemDto> timeline;
  private long projectionRevision;
}
