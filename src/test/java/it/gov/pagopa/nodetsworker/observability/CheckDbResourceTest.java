package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import it.gov.pagopa.nodetsworker.observability.repository.models.AttemptEntity;
import it.gov.pagopa.nodetsworker.observability.repository.models.PaymentEntity;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CheckDbResourceTest {
  private static final String PA = "90000000077";
  private static final String NOTICE = "990000000000000077";
  private static final String ATTEMPT = "check-db-attempt";
  private static final String TOKEN = "check-db-token-never-in-response";
  private static final String PATH = "/api/attempts/" + ATTEMPT + "/check-db";

  @Inject @PersistenceUnit("observability") EntityManager em;
  @Inject ProjectionRepository projection;
  @Inject @io.quarkus.agroal.DataSource("online-ndp001") DataSource ndp001;
  @Inject @io.quarkus.agroal.DataSource("online-ndp004") DataSource ndp004;
  @ConfigProperty(name = "snapshot.online.nodes.online-ndp001.service-identifier") String service001;
  @ConfigProperty(name = "snapshot.online.nodes.online-ndp004.service-identifier") String service004;

  @BeforeEach
  void seed() throws SQLException {
    createOnlineSchema(ndp001, "PAID", "NOTIFIED");
    createOnlineSchema(ndp004, "INSERTED", "CANCELLED");
    QuarkusTransaction.requiringNew().run(() -> {
      var payment = PaymentEntity.builder().paFiscalCode(PA).noticeNumber(NOTICE)
          .currentState("PAYING").projectionRevision(7).build();
      var attempt = AttemptEntity.builder().attemptId(ATTEMPT).payment(payment)
          .paymentToken(TOKEN).serviceIdentifier(service001).currentState("PAYMENT_UNKNOWN")
          .firstSeenAt(Instant.now()).expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
          .startTimestampBasis(TimestampBasis.SOURCE_EVENT).build();
      payment.getAttempts().add(attempt);
      em.persist(payment);
    });
  }

  @AfterEach
  void cleanProjection() {
    QuarkusTransaction.requiringNew().run(() ->
        em.createQuery("SELECT p FROM PaymentEntity p WHERE p.paFiscalCode = :pa", PaymentEntity.class)
            .setParameter("pa", PA).getResultList().forEach(em::remove));
  }

  @Test
  void returnsRealStatesAndNeverChangesProjectionOrExposesToken() {
    var response = given().get(PATH).then().statusCode(200)
        .header("Cache-Control", "no-store")
        .body("source", equalTo("NODO_ONLINE"))
        .body("attemptId", equalTo(ATTEMPT))
        .body("paFiscalCode", equalTo(PA))
        .body("noticeNumber", equalTo(NOTICE))
        .body("serviceIdentifier", equalTo(service001))
        .body("projectionRevision", equalTo(7))
        .body("checkedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"))
        .body("observed.logicalState", equalTo("PAYING"))
        .body("observed.attemptState", equalTo("PAYMENT_UNKNOWN"))
        .body("database.logicalState", equalTo("PAID"))
        .body("database.attemptState", equalTo("NOTIFIED"))
        .extract().asString();
    assertFalse(response.contains(TOKEN));
    QuarkusTransaction.requiringNew().run(() -> {
      var entity = em.createQuery("SELECT a FROM AttemptEntity a WHERE a.attemptId = :id", AttemptEntity.class)
          .setParameter("id", ATTEMPT).getSingleResult();
      assertEquals("PAYMENT_UNKNOWN", entity.getCurrentState());
      assertEquals("PAYING", entity.getPayment().getCurrentState());
      assertEquals(7, entity.getPayment().getProjectionRevision());
      assertEquals(0, entity.getTimeline().size());
      assertEquals(0, entity.getPayment().getTimeline().size());
      assertEquals(0L, em.createQuery("SELECT COUNT(o) FROM OutboxEntity o WHERE o.paFiscalCode = :pa", Long.class)
          .setParameter("pa", PA).getSingleResult());
    });
  }

  @Test
  void routesTheSameKeysToTheOtherConfiguredDatabase() {
    updateAttempt("serviceIdentifier", service004);
    given().get(PATH).then().statusCode(200)
        .body("serviceIdentifier", equalTo(service004))
        .body("database.logicalState", equalTo("INSERTED"))
        .body("database.attemptState", equalTo("CANCELLED"));
  }

  @Test
  void missingSnapshotsAreIndependentExplicitNulls() throws SQLException {
    execute(ndp001, "DELETE FROM online.position_status_snapshot");
    given().get(PATH).then().statusCode(200)
        .body("database.containsKey('logicalState')", equalTo(true))
        .body("database.logicalState", nullValue())
        .body("database.attemptState", equalTo("NOTIFIED"));
    execute(ndp001, "DELETE FROM online.position_payment_status_snapshot");
    given().get(PATH).then().statusCode(200)
        .body("database.containsKey('attemptState')", equalTo(true))
        .body("database.attemptState", nullValue());
  }

  @Test
  void rejectsMissingOrUnconfiguredServicesWithoutDefaultingToAnotherDatabase() {
    updateAttempt("serviceIdentifier", null);
    given().get(PATH).then().statusCode(422).body("code", equalTo("DB_SERVICE_MISSING"));
    updateAttempt("serviceIdentifier", "not-a-configured-node");
    given().get(PATH).then().statusCode(422).body("code", equalTo("DB_SERVICE_UNCONFIGURED"));
  }

  @Test
  void projectionLookupUsesCallerVisibilityAndDoesNotRequireTheRetentionSweep() {
    assertTrue(projection.findDbCheckContext(ATTEMPT, "90000000099", Instant.now()).isEmpty());
    assertTrue(projection.findDbCheckContext(ATTEMPT, PA, Instant.now()).isPresent());
  }

  @Test
  void absentOrExpiredProjectionAttemptsAreNotChecked() {
    given().get("/api/attempts/missing/check-db").then().statusCode(404).body("code", equalTo("NOT_FOUND"));
    updateAttempt("expiresAt", Instant.now().minusSeconds(1));
    given().get(PATH).then().statusCode(404).body("code", equalTo("NOT_FOUND"));
  }

  @Test
  void refusesClientRoutingOrTokenOverrides() {
    given().queryParam("serviceIdentifier", service004).get(PATH).then().statusCode(400);
    given().queryParam("paymentToken", "override").get(PATH).then().statusCode(400);
  }

  @Test
  void duplicateOrUnsupportedSnapshotsReturnExplicitErrors() throws SQLException {
    execute(ndp001, "INSERT INTO online.position_status_snapshot (id,pa_fiscal_code,notice_id,status) "
        + "SELECT 2,pa_fiscal_code,notice_id,status FROM online.position_status_snapshot WHERE id = 1");
    given().get(PATH).then().statusCode(500).body("code", equalTo("INTERNAL_ERROR"));
    execute(ndp001, "DELETE FROM online.position_status_snapshot WHERE id = 2");
    execute(ndp001, "UPDATE online.position_payment_status_snapshot SET status = 'INSERTED'");
    given().get(PATH).then().statusCode(500).body("code", equalTo("INTERNAL_ERROR"));
  }

  @Test
  void databaseFailuresAreNotPartialSuccessesAndDoNotExposeSql() throws SQLException {
    execute(ndp001, "DROP TABLE online.position_payment_status_snapshot");
    given().get(PATH).then().statusCode(503).body("code", equalTo("DB_UNAVAILABLE"))
        .body("message", not(containsString("SELECT")))
        .body("message", not(containsString(TOKEN)));
  }

  private void updateAttempt(String field, Object value) {
    QuarkusTransaction.requiringNew().run(() ->
        em.createQuery("UPDATE AttemptEntity a SET a." + field + " = :value WHERE a.attemptId = :id")
            .setParameter("value", value).setParameter("id", ATTEMPT).executeUpdate());
  }

  private void createOnlineSchema(DataSource source, String logicalState, String attemptState)
      throws SQLException {
    try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
      assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
      statement.execute("CREATE SCHEMA IF NOT EXISTS online");
      statement.execute("DROP TABLE IF EXISTS online.position_status_snapshot");
      statement.execute("DROP TABLE IF EXISTS online.position_payment_status_snapshot");
      statement.execute("CREATE TABLE online.position_status_snapshot "
          + "(id BIGINT PRIMARY KEY, pa_fiscal_code VARCHAR(50), notice_id VARCHAR(50), status VARCHAR(50))");
      statement.execute("CREATE TABLE online.position_payment_status_snapshot "
          + "(id BIGINT PRIMARY KEY, pa_fiscal_code VARCHAR(50), notice_id VARCHAR(50), payment_token VARCHAR(128), status VARCHAR(50))");
      try (var logical = connection.prepareStatement("INSERT INTO online.position_status_snapshot VALUES (1,?,?,?)");
          var attempt = connection.prepareStatement("INSERT INTO online.position_payment_status_snapshot VALUES (1,?,?,?,?)")) {
        logical.setString(1, PA);
        logical.setString(2, NOTICE);
        logical.setString(3, logicalState);
        logical.executeUpdate();
        attempt.setString(1, PA);
        attempt.setString(2, NOTICE);
        attempt.setString(3, TOKEN);
        attempt.setString(4, attemptState);
        attempt.executeUpdate();
      }
    }
  }

  private void execute(DataSource source, String sql) throws SQLException {
    try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
