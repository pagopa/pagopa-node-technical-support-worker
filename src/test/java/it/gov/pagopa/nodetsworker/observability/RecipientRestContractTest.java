package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.observability.ingestion.ReIngestionService;
import it.gov.pagopa.nodetsworker.observability.ingestion.ReMessage;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RecipientRestContractTest {
  private static final String PA = "90000000008";
  private static final String NOTICE = "990000000000000008";
  @Inject ReIngestionService ingestion;
  @Inject @PersistenceUnit("observability") EntityManager em;

  @Test
  void recipientOnlySnapshotsContainStrictRequiredSupportAndProvenance() {
    try {
      ReMessage message = new ReMessage();
      message.setEsito("CAMBIO_STATO");
      message.setStatus("receipt_recipient_NOTICE_GENERATED");
      message.setIdDominio(PA);
      message.setNoticeNumber(NOTICE);
      message.setUniqueId("recipient-rest-contract");
      message.setDataOraEvento("2026-10-20T10:00:00.000");
      message.setTipoEvento("actualReceiptGeneration");
      ingestion.ingest(message);

      given().get("/api/payments/" + PA + "/" + NOTICE).then().statusCode(200)
          .body("state", nullValue())
          .body("attempts.size()", equalTo(0))
          .body("recipients.size()", equalTo(1))
          .body("recipients[0].stazioneRecipient", nullValue())
          .body("recipients[0].keySet().size()", equalTo(9))
          .body("recipients[0].timeline[0].trigger", equalTo("actualReceiptGeneration"))
          .body("recipients[0].timeline[0].timestampBasis", equalTo("SOURCE_EVENT"));
      for (String endpoint : new String[] {"/api/overview", "/api/creditors/" + PA}) {
        // The recipient event is dated 2026-10-20 (local time): the interval must contain it to be counted.
        given().queryParam("paFiscalCode", PA).queryParam("from", "2026-10-20T00:00:00.000Z")
            .queryParam("to", "2026-10-21T00:00:00.000Z").get(endpoint).then().statusCode(200)
            .body("recipientStates.find { it.state == 'NOTICE_GENERATED' }.count", equalTo(1))
            .body("matrices.recipient.size()", equalTo(1))
            .body("matrices.recipient[0].value", nullValue());
      }
    } finally {
      QuarkusTransaction.requiringNew().run(() -> {
        em.createQuery("SELECT p FROM PaymentEntity p WHERE p.paFiscalCode = :pa")
            .setParameter("pa", PA).getResultList().forEach(em::remove);
        em.createQuery("DELETE FROM OutboxEntity o WHERE o.paFiscalCode = :pa")
            .setParameter("pa", PA).executeUpdate();
        em.createQuery("DELETE FROM InboxEntity i WHERE i.uniqueId = :key")
            .setParameter("key", "recipient-rest-contract").executeUpdate();
      });
    }
  }
}
