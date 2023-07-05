package it.gov.pagopa.nodetsworker.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositiveBizEvent  {
  private String version;
  private String id;
  private String idPaymentManager;
  private String receiptId;
  private DebtorPosition debtorPosition;
  private Creditor creditor;
  private Psp psp;
  private Subject debtor;
  private Subject payer;
  private PaymentInfo paymentInfo;
  private List<Transfer> transferList;
  private Object transactionDetails;
  private LocalDateTime timestamp;
}
