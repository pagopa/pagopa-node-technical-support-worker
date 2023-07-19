package it.gov.pagopa.nodetsworker.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInfo {
  private String description;
  private LocalDateTime paymentDateTime;
  private String applicationDate;
  private String transferDate;
  private String dueDate;
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
