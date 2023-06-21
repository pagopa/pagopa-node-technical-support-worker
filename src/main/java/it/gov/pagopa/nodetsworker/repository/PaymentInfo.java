package it.gov.pagopa.nodetsworker.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInfo {
  private String description;
  private LocalDateTime paymentDateTime;
  private LocalDate applicationDate;
  private LocalDate transferDate;
  private LocalDate dueDate;
  private String paymentToken;
  private BigDecimal amount;
  private BigDecimal fee;
  private BigDecimal primaryCiIncurredFee;
  private String idBundle;
  private String idCiBundle;
  private Long totalNotice;
  private String paymentMethod;
  private String touchpoint;
  private String remittanceInformation;
  private Object metadata;
  private String IUR;
}
