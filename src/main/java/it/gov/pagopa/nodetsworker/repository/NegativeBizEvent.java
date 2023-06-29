package it.gov.pagopa.nodetsworker.repository;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
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

}
