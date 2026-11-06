package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import it.gov.pagopa.nodetsworker.observability.ingestion.IngestionProjectionWriter;
import it.gov.pagopa.nodetsworker.observability.repository.OnlineStatusRepository;
import it.gov.pagopa.nodetsworker.observability.repository.OnlineStatusSnapshot;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlineStatusSyncResourceTest {

  private static final String PA = "90000000001";
  private static final String NOTICE = "790000000000000041";
  private static final String TOKEN = "resource-test-payment-token";

  @InjectMock
  OnlineStatusRepository onlineRepository;

  @Inject
  IngestionProjectionWriter writer;

  @BeforeEach
  void resetOnlineRepositoryMock() {
    reset(onlineRepository);
  }

  @Test
  void paymentSyncReturnsSyncedState() throws SQLException {
    when(onlineRepository.findPaymentStatus(PA, NOTICE)).thenReturn(Optional.of(
        new OnlineStatusSnapshot("PAID", LocalDateTime.parse("2024-06-15T10:00:00"),
            LocalDateTime.parse("2024-06-15T10:01:00"))));

    given().contentType("application/json")
        .body(Map.of("paFiscalCode", PA, "noticeNumber", NOTICE))
        .post("/internal/observability/sync/payment")
        .then().statusCode(200)
        .body("outcome", equalTo("SYNCED"))
        .body("state", equalTo("PAID"));
  }

  @Test
  void attemptSyncUsesFixedRouteAndDoesNotEchoRawToken() throws SQLException {
    writer.reconcileLogical(PA, NOTICE, "PAYING", java.time.Instant.parse("2024-06-15T08:00:00Z"));
    when(onlineRepository.findAttemptStatus(PA, NOTICE, TOKEN)).thenReturn(Optional.of(
        new OnlineStatusSnapshot("PAYMENT_RESERVED", LocalDateTime.parse("2024-06-15T10:00:00"),
            LocalDateTime.parse("2024-06-15T10:01:00"))));

    String response = given().contentType("application/json")
        .body(Map.of("paFiscalCode", PA, "noticeNumber", NOTICE, "paymentToken", TOKEN))
        .post("/internal/observability/sync/attempt")
        .then().statusCode(200)
        .body("outcome", equalTo("SYNCED"))
        .body("state", equalTo("PAYMENT_RESERVED"))
        .extract().asString();

    org.junit.jupiter.api.Assertions.assertFalse(response.contains(TOKEN));
  }

  @Test
  void missingSourceAndAlreadyCurrentPaymentReturnNoop() throws SQLException {
    when(onlineRepository.findPaymentStatus(PA, NOTICE)).thenReturn(Optional.empty());
    given().contentType("application/json")
        .body(Map.of("paFiscalCode", PA, "noticeNumber", NOTICE))
        .post("/internal/observability/sync/payment")
        .then().statusCode(200)
        .body("outcome", equalTo("NOOP"));

    String currentNotice = "790000000000000042";
    java.time.Instant at = java.time.Instant.parse("2024-06-15T08:00:00Z");
    writer.reconcileLogical(PA, currentNotice, "PAYING", at);
    when(onlineRepository.findPaymentStatus(PA, currentNotice)).thenReturn(Optional.of(
        new OnlineStatusSnapshot("PAYING", LocalDateTime.parse("2024-06-15T10:00:00"),
            LocalDateTime.parse("2024-06-15T10:01:00"))));

    given().contentType("application/json")
        .body(Map.of("paFiscalCode", PA, "noticeNumber", currentNotice))
        .post("/internal/observability/sync/payment")
        .then().statusCode(200)
        .body("outcome", equalTo("NOOP"))
        .body("state", equalTo("PAYING"));
  }

  @Test
  void invalidPaymentAndAttemptKeysReturnBadRequest() {
    given().contentType("application/json")
        .body(Map.of("paFiscalCode", "invalid", "noticeNumber", NOTICE))
        .post("/internal/observability/sync/payment")
        .then().statusCode(400);

    given().contentType("application/json")
        .post("/internal/observability/sync/payment")
        .then().statusCode(400);

    given().contentType("application/json")
        .body(Map.of("paFiscalCode", PA, "noticeNumber", NOTICE, "paymentToken", " "))
        .post("/internal/observability/sync/attempt")
        .then().statusCode(400);
  }

  @Test
  void unsupportedOnlineStateReturnsBadGateway() throws SQLException {
    when(onlineRepository.findPaymentStatus(PA, NOTICE)).thenReturn(Optional.of(
        new OnlineStatusSnapshot("UNKNOWN", LocalDateTime.parse("2024-06-15T10:00:00"),
            LocalDateTime.parse("2024-06-15T10:01:00"))));

    given().contentType("application/json")
        .body(Map.of("paFiscalCode", PA, "noticeNumber", NOTICE))
        .post("/internal/observability/sync/payment")
        .then().statusCode(502)
        .body("code", equalTo("UNSUPPORTED_ONLINE_STATE"));
  }

  @Test
  void sourceDatabaseFailureIsNotConvertedToNoop() throws SQLException {
    when(onlineRepository.findPaymentStatus(PA, NOTICE)).thenThrow(new SQLException("offline"));

    String response = given().contentType("application/json")
        .body(Map.of("paFiscalCode", PA, "noticeNumber", NOTICE))
        .post("/internal/observability/sync/payment")
        .then().statusCode(500)
        .extract().asString();

    org.junit.jupiter.api.Assertions.assertFalse(response.contains("NOOP"));
  }
}
