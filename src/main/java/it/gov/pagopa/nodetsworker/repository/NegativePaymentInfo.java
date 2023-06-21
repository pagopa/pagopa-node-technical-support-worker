package it.gov.pagopa.nodetsworker.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
class NegativePaymentInfo {
  private LocalDateTime paymentDateTime;
  private LocalDate dueDate;
  private String paymentToken;
  private BigDecimal amount;
  private Long totalNotice;
  private String paymentMethod;
  private String touchpoint;
  private String remittanceInformation;
  private Object metadata;
}
