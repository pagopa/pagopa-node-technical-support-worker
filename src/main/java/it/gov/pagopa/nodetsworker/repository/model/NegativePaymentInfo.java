package it.gov.pagopa.nodetsworker.repository.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NegativePaymentInfo {
  private String paymentDateTime;
  private LocalDate dueDate;
  private String paymentToken;
  private BigDecimal amount;
  private Long totalNotice;
  private String paymentMethod;
  private String touchpoint;
  private String remittanceInformation;
  private Object metadata;
}
