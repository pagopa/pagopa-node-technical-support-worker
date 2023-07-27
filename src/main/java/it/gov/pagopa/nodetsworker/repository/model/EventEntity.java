package it.gov.pagopa.nodetsworker.repository.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.panache.common.Parameters;
import lombok.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Data
@EqualsAndHashCode(callSuper = true)
@MongoEntity(collection = "events")
public class EventEntity extends PanacheMongoEntity{

  private String insertedTimestamp;
  private String componente;
  private String categoriaEvento;
  private String sottoTipoEvento;
  private String idDominio;
  private String iuv;
  private String ccp;
  private String psp;
  private String tipoVersamento;
  private String tipoEvento;
  private String fruitore;
  private String erogatore;
  private String stazione;
  private String canale;
  private String parametriSpecificiInterfaccia;
  private String esito;
  private String sessionId;
  private String status;
  private String payload;
  private String info;
  private String businessProcess;
  private String fruitoreDescr;
  private String erogatoreDescr;
  private String pspDescr;
  private String noticeNumber;
  private String creditorReferenceId;
  private String paymentToken;
  private String sessionIdOriginal;
  private String dataOraEvento;
  private String uniqueId;
  private String version;
  private Long timestamp;
  private String serviceIdentifier;

  private static String dateFilter = "PartitionKey >= :from and PartitionKey <= :to";
  private static Parameters dateParams(LocalDate dateFrom, LocalDate dateTo){
    return Parameters.with("from", DateTimeFormatter.ISO_DATE.format(dateFrom)+"T00")
            .and("to", DateTimeFormatter.ISO_DATE.format(dateTo)+"T23");
  }

  public static PanacheQuery<EventEntity> findReByCiAndNN(
          String creditorInstitution, String nav, LocalDate dateFrom, LocalDate dateTo) {
    return find(
            dateFilter +
                    " and idDominio = :idDominio and noticeNumber = :noticeNumber and esito = 'CAMBIO_STATO'"
                    + " and status like 'payment_'",
            dateParams(dateFrom,dateTo)
                    .and("idDominio", creditorInstitution)
                    .and("noticeNumber", nav)
    )
            .project(EventEntity.class);
  }

  public static PanacheQuery<EventEntity> findReByCiAndNNAndToken(
          String creditorInstitution,
          String nav,
          String paymentToken,
          LocalDate dateFrom,
          LocalDate dateTo) {
    return find(
            dateFilter +
                    " and idDominio = :idDominio and noticeNumber = :noticeNumber and paymentToken = :paymentToken and esito = 'CAMBIO_STATO'"
                    + " and status like 'payment_'",
            dateParams(dateFrom,dateTo)
                    .and("idDominio", creditorInstitution)
                    .and("noticeNumber", nav)
                    .and("paymentToken", paymentToken)
            )
            .project(EventEntity.class);
  }

  public static PanacheQuery<EventEntity> findReByCiAndIUV(
          String creditorInstitution, String iuv, LocalDate dateFrom, LocalDate dateTo) {
    return find(
            dateFilter +
                    " and idDominio = :idDominio and iuv = :iuv and esito = 'CAMBIO_STATO'"
                    + " and status like 'payment_'",
            dateParams(dateFrom,dateTo)
                    .and("idDominio", creditorInstitution)
                    .and("iuv", iuv))
            .project(EventEntity.class);
  }

  public static PanacheQuery<EventEntity> findReByCiAndIUVAndCCP(
          String creditorInstitution, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo) {
    return find(
            dateFilter +
                    " and idDominio = :idDominio and iuv = :iuv and ccp = :ccp and esito = 'CAMBIO_STATO'"
                    + " and status like 'payment_'",
            dateParams(dateFrom,dateTo)
                    .and("idDominio", creditorInstitution)
                    .and("iuv", iuv)
                    .and("ccp", ccp)
    )
            .project(EventEntity.class);
  }
}
