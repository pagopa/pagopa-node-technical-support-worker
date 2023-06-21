package it.gov.pagopa.nodetsworker.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@MongoEntity(collection = "biz-events", clientName = "biz")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositiveBizEvent extends PanacheMongoEntity {
  private String version;
  private String id;
//  private String idPaymentManager;
//  private String receiptId;
  private DebtorPosition debtorPosition;
//  private Creditor creditor;
//  private Psp psp;
//  private Subject debtor;
//  private Subject payer;
//  private PaymentInfo paymentInfo;
//  private Transfer transferList;
//  private Object transactionDetails;


  public static PanacheQuery<PositiveBizEvent> findByCIAndNAV(String creditorInstitution, String nav, LocalDate dateFrom, LocalDate dateTo) {
    return find("idDominio", creditorInstitution).project(PositiveBizEvent.class);
  }
  public static PanacheQuery<PositiveBizEvent> findByCIAndIUV(String creditorInstitution, String nav, LocalDate dateFrom, LocalDate dateTo) {
    return find("idDominio", creditorInstitution).project(PositiveBizEvent.class);
  }
}
