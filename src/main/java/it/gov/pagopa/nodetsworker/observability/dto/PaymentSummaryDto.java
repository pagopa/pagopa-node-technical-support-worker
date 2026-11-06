package it.gov.pagopa.nodetsworker.observability.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Contract schema {@code PaymentSummary}: a logical payment row for lists and payment deltas. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummaryDto {
  private String paFiscalCode;
  private String noticeNumber;
  @Schema(nullable = true, enumeration = {"INSERTED", "PAYING", "PAID", "NOTICE_STORED", "NOTIFIED"},
      description = "Logical position state; null while no position event or logical source snapshot has been observed. Never derived from attempts or recipients.")
  private String state;
  private long projectionRevision;
  private boolean timelineIncomplete;
  private boolean hasAnomaly;
  private boolean warning;
  private int attemptCount;
}
