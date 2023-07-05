package it.gov.pagopa.nodetsworker.repository.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.PanacheQuery;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.panache.common.Parameters;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@MongoEntity(collection = "events", clientName = "events")
public class EventEntity extends PanacheMongoEntity {

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

  public static PanacheQuery<EventEntity> findByCIAndNAV(
      String creditorInstitution, String nav, LocalDate dateFrom, LocalDate dateTo) {
    return find(
            "idDominio = :idDominio and noticeNumber = :noticeNumber and esito = 'CAMBIO_STATO' and"
                + " status like 'payment_'",
            Parameters.with("idDominio", creditorInstitution).and("noticeNumber", nav))
        .project(EventEntity.class);
  }

  public static PanacheQuery<EventEntity> findByCIAndNAVAndToken(
      String creditorInstitution,
      String nav,
      String paymentToken,
      LocalDate dateFrom,
      LocalDate dateTo) {
    return find(
            "idDominio = :idDominio and noticeNumber = :noticeNumber and esito = 'CAMBIO_STATO' and"
                + " status like 'payment_'",
            Parameters.with("idDominio", creditorInstitution).and("noticeNumber", nav))
        .project(EventEntity.class);
  }

  public static PanacheQuery<EventEntity> findByCIAndIUV(
      String creditorInstitution, String iuv, LocalDate dateFrom, LocalDate dateTo) {
    return find(
            "idDominio = :idDominio and iuv = :iuv and esito = 'CAMBIO_STATO' and status like"
                + " 'payment_'",
            Parameters.with("idDominio", creditorInstitution).and("iuv", iuv))
        .project(EventEntity.class);
  }

  public static PanacheQuery<EventEntity> findByCIAndIUVAndCCP(
      String creditorInstitution, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo) {
    return find(
            "idDominio = :idDominio and iuv = :iuv and esito = 'CAMBIO_STATO' and status like"
                + " 'payment_'",
            Parameters.with("idDominio", creditorInstitution).and("iuv", iuv))
        .project(EventEntity.class);
  }
}
