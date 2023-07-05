package it.gov.pagopa.nodetsworker.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NegativeBizEvent {
  private String version;
  private String id;
  private String useCase;
  private String businessProcess;
  private Boolean reAwakable;
  private DebtorPosition debtorPosition;
  private Creditor creditor;
  private Psp psp;
  private Subject debtor;
  private NegativePaymentInfo paymentInfo;
  private List<NegativeTransfer> transferList;
  private Object transactionDetails;
  private Long timestamp;
}
