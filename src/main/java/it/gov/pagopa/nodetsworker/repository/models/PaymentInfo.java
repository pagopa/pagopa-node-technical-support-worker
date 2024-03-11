package it.gov.pagopa.nodetsworker.repository.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInfo {
  private String description;
  private String paymentDateTime;
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
