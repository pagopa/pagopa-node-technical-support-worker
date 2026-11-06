package it.gov.pagopa.nodetsworker.observability.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** The safe attempt projection (contract schema {@code Attempt}); never carries the raw payment token. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(requiredProperties = {"attemptId", "serviceIdentifier", "psp", "stazione", "canale",
    "attemptStartedAt", "startTimestampBasis", "timelineIncomplete", "timeline", "paFiscalCode",
    "noticeNumber", "state", "warning", "hasAnomaly", "tokenDisplay", "projectionRevision"},
    additionalProperties = Schema.False.class)
@JsonPropertyOrder({"attemptId", "serviceIdentifier", "psp", "stazione", "canale", "attemptStartedAt",
    "startTimestampBasis", "timelineIncomplete", "timeline", "paFiscalCode", "noticeNumber",
    "state", "warning", "hasAnomaly", "tokenDisplay", "projectionRevision"})
public class AttemptDto {
  private String attemptId;
  @Schema(nullable = true)
  private String serviceIdentifier;
  @Schema(nullable = true)
  private String psp;
  @Schema(nullable = true)
  private String stazione;
  @Schema(nullable = true)
  private String canale;
  @Schema(nullable = true, format = "date-time")
  private String attemptStartedAt;
  private TimestampBasis startTimestampBasis;
  private boolean timelineIncomplete;
  private List<TimelineItemDto> timeline;
  private String paFiscalCode;
  private String noticeNumber;
  @Schema(enumeration = {"PAYING", "PAYING_RPT", "PAID_NORPT", "PAID", "FAILED_NORPT", "FAILED",
      "CANCELLED", "CANCELLED_NORPT", "NOTICE_GENERATED", "NOTICE_STORED", "NOTICE_SENT", "NOTIFIED",
      "PAYMENT_RESERVED", "PAYMENT_SENT", "PAYMENT_ACCEPTED", "PAYMENT_REFUSED", "PAYMENT_UNKNOWN",
      "PAYMENT_SEND_ERROR"})
  private String state;
  private boolean warning;
  private boolean hasAnomaly;
  private String tokenDisplay;
  private long projectionRevision;
}
