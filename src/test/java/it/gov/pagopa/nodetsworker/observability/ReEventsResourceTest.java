package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.mockito.InjectMock;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import it.gov.pagopa.nodetsworker.observability.re.ReEvent;
import it.gov.pagopa.nodetsworker.observability.re.ReKustoSource;
import it.gov.pagopa.nodetsworker.observability.re.ReSource;
import it.gov.pagopa.nodetsworker.observability.repository.models.AttemptEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PaymentEntity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ReEventsResourceTest.SyntheticRe.class)
class ReEventsResourceTest {
  private static final String PATH = "/api/attempts/re-synthetic/re-events";
  @Inject @PersistenceUnit("observability") EntityManager em;
  @Inject ObjectMapper mapper;
  @InjectMock ReKustoSource source;
  private String body;
  private Instant from;

  public static class SyntheticRe implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.ofEntries(
          Map.entry("observability.re.enabled", "true"),
          Map.entry("observability.re.schema-verified", "true"),
          Map.entry("observability.re.timezone-verified", "true"),
          Map.entry("observability.re.retention-verified", "true"),
          Map.entry("observability.re.ordering-verified", "true"),
          Map.entry("observability.re.watermark-verified", "true"),
          Map.entry("observability.re.personal-data-approved", "true"),
          Map.entry("observability.re.endpoint", "https://synthetic.kusto.windows.net"),
          Map.entry("observability.re.client-id", "synthetic-client"),
          Map.entry("observability.re.client-secret", "synthetic-secret"),
          Map.entry("observability.re.tenant-id", "synthetic-tenant"),
          Map.entry("observability.re.database", "synthetic"),
          Map.entry("observability.re.table", "ReEvent"),
          Map.entry("observability.re.row-id-column", "technicalRowId"),
          Map.entry("observability.re.retention-days", "60"));
    }
  }

  @BeforeEach
  void seed() {
    from = Instant.now().minusSeconds(3600);
    body = "{\"from\":\"" + from + "\",\"to\":\"" + from.plusSeconds(1800) + "\"}";
    QuarkusTransaction.requiringNew().run(() -> {
      var payment = PaymentEntity.builder().paFiscalCode("90000000088").noticeNumber("990000000000000088")
          .currentState("PAYING").projectionRevision(7).build();
      payment.getAttempts().add(AttemptEntity.builder().attemptId("re-synthetic").payment(payment)
          .paymentToken("synthetic-token").currentState("PAYMENT_UNKNOWN").firstSeenAt(from)
          .expiresAt(Instant.now().plusSeconds(3600)).startTimestampBasis(TimestampBasis.SOURCE_EVENT).build());
      em.persist(payment);
    });
    when(source.read(any())).thenReturn(List.of(new ReSource.Row(from, "stable-1",
        Map.of("insertedTimestamp", from.toString(), "status", "FUTURE_STATE",
            "standIn", true, "version", 7, "payload", "Y2l0dMOg"))));
  }

  @AfterEach
  void cleanup() {
    QuarkusTransaction.requiringNew().run(() ->
        em.createQuery("SELECT p FROM PaymentEntity p WHERE p.paFiscalCode = '90000000088'", PaymentEntity.class)
            .getResultList().forEach(em::remove));
  }

  @Test
  void realEndpointReturnsFlatFullContractAndDoesNotMutateTheProjection() {
    given().contentType("application/json").body(body).post(PATH).then().statusCode(200)
        .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
        .body("source", equalTo("RE")).body("attemptId", equalTo("re-synthetic"))
        .body("coverage", equalTo("WINDOW_ONLY")).body("nextCursor", nullValue())
        .body("events[0].status", equalTo("FUTURE_STATE")).body("events[0].standIn", equalTo(true))
        .body("events[0].version", equalTo(7)).body("events[0].info", nullValue())
        .body("events[0].payload", equalTo("Y2l0dMOg"))
        .body("events[0].payloadDecoded.status", equalTo("DECODED"))
        .body("events[0].payloadDecoded.text", equalTo("città"));
    QuarkusTransaction.requiringNew().run(() -> assertEquals("PAYMENT_UNKNOWN",
        em.createQuery("SELECT a FROM AttemptEntity a WHERE a.attemptId = 're-synthetic'", AttemptEntity.class)
            .getSingleResult().getCurrentState()));
  }

  @Test
  void validatesBodyAndQueryAndDistinguishesMissingFromExpired() {
    for (String invalid : List.of("{}", "null", "{\"from\":true,\"to\":\"x\"}", "{\"from\":",
        "{\"from\":\"x\",\"to\":\"y\",\"paymentToken\":\"not-accepted\"}")) {
      given().contentType("application/json").body(invalid).post(PATH).then().statusCode(400)
          .header("Cache-Control", "no-store");
    }
    given().contentType("application/json").body(body).queryParam("token", "not-accepted")
        .post(PATH).then().statusCode(400).header("Cache-Control", "no-store");
    given().urlEncodingEnabled(false).contentType("application/json").body(body).queryParam("token", "not-accepted")
        .post(PATH + ";v=1").then().statusCode(400).header("Cache-Control", "no-store");
    given().contentType("application/json").body(body).post("/api/attempts/missing/re-events")
        .then().statusCode(404).header("Cache-Control", "no-store");
    QuarkusTransaction.requiringNew().run(() ->
        em.createQuery("UPDATE AttemptEntity a SET a.expiresAt = :expired WHERE a.attemptId = 're-synthetic'")
            .setParameter("expired", Instant.now().minusSeconds(1)).executeUpdate());
    given().contentType("application/json").body(body).post(PATH).then().statusCode(410)
        .header("Cache-Control", "no-store");
  }

  @Test
  void generatedOpenApiDocumentsPostAndAll33NullableScalarFields() throws Exception {
    JsonNode api = mapper.readTree(given().queryParam("format", "json").get("/q/openapi")
        .then().statusCode(200).extract().asString());
    var operation = api.path("paths").path("/api/attempts/{attemptId}/re-events").path("post");
    assertTrue(operation.path("requestBody").path("required").asBoolean());
    for (String status : List.of("200", "400", "404", "410", "429", "502", "503", "504")) {
      assertTrue(operation.path("responses").has(status), status);
    }
    var event = api.at("/components/schemas/ReEvent");
    assertEquals(36, event.path("properties").size());
    for (String field : ReEvent.FIELDS) {
      assertTrue(event.path("properties").has(field), field);
      assertTrue(event.path("properties").path(field).path("nullable").asBoolean(), field);
      if (!field.equals("payload")) {
        assertFalse(event.path("properties").path(field).has("type"), field + " must not constrain scalars to objects");
        assertTrue(event.path("properties").path(field).path("oneOf").get(0).path("nullable").asBoolean());
      }
    }
    assertEquals(36, event.path("required").size());
    assertEquals(2, api.at("/components/schemas/ReRequest/required").size());
    assertEquals(100, api.at("/components/schemas/ReResponse/properties/events/maxItems").asInt());
    JsonNode published = mapper.readTree(java.nio.file.Path.of("openapi/openapi.json").toFile());
    assertEquals(operation, published.path("paths").path("/api/attempts/{attemptId}/re-events").path("post"));
    for (String name : List.of("ReRequest", "ReResponse", "ReWindow", "ReEvent", "RePayloadDecoded", "ReScalar")) {
      assertEquals(api.at("/components/schemas").path(name), published.at("/components/schemas").path(name), name);
    }
  }
}
