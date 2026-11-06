package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectMock;
import it.gov.pagopa.nodetsworker.observability.monitoring.CacheVersion;
import it.gov.pagopa.nodetsworker.observability.monitoring.NodoMonitoringClient;
import jakarta.ws.rs.ProcessingException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(NodoMonitoringResourceTest.MonitoringProfile.class)
class NodoMonitoringResourceTest {
  private static final String PATH = "/api/nodo-monitoring/cache/version";

  @InjectMock @RestClient NodoMonitoringClient client;

  @Test
  void aggregatesBothNodesSendingTargetAndSubscriptionKey() {
    when(client.cacheVersion("NDP001DEV", "key-001")).thenReturn(CompletableFuture.completedFuture(
        new CacheVersion("UP", "111", "3.42.1", "DEV", "NDP001DEV")));
    when(client.cacheVersion("NDP004IT", "key-004")).thenReturn(CompletableFuture.completedFuture(
        new CacheVersion("UP", "2418234202541422", "3.42.2-snapshot", "SIT", "NDP004IT")));

    given().when().get(PATH).then()
        .statusCode(200)
        .header("Cache-Control", containsString("no-store"))
        .body("checkedAt", notNullValue())
        .body("nodes.node", contains("NDP001", "NDP004"))
        .body("nodes.outcome", contains("OK", "OK"))
        .body("nodes[0].version", equalTo("3.42.1"))
        .body("nodes[1].cacheId", equalTo("2418234202541422"))
        .body("nodes[1].environment", equalTo("SIT"))
        .body("nodes[1].serviceIdentifier", equalTo("NDP004IT"))
        .body("nodes[1].dbStatus", equalTo("UP"))
        .body(not(containsString("key-00")));
  }

  @Test
  void reportsFailingNodeWithoutHidingTheOther() {
    when(client.cacheVersion("NDP001DEV", "key-001"))
        .thenReturn(CompletableFuture.failedFuture(new ProcessingException("boom key-001")));
    when(client.cacheVersion("NDP004IT", "key-004")).thenReturn(CompletableFuture.completedFuture(
        new CacheVersion("UP", "2418234202541422", "3.42.2-snapshot", "SIT", "NDP004IT")));

    given().when().get(PATH).then()
        .statusCode(200)
        .body("nodes.outcome", contains("UNAVAILABLE", "OK"))
        .body("nodes[0].target", equalTo("NDP001DEV"))
        .body("nodes[0]", hasKey("version"))
        .body("nodes[0].version", nullValue())
        .body("nodes[1].version", equalTo("3.42.2-snapshot"))
        .body(not(containsString("key-00")));
  }

  @Test
  void rejectsQueryParameters() {
    given().when().get(PATH + "?unexpected=value").then()
        .statusCode(400)
        .body("code", equalTo("INVALID_REQUEST"));
    verify(client, never()).cacheVersion(anyString(), anyString());
  }

  public static class MonitoringProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "observability.monitoring.ndp001.target", "NDP001DEV",
          "observability.monitoring.ndp001.subscription-key", "key-001",
          "observability.monitoring.ndp004.target", "NDP004IT",
          "observability.monitoring.ndp004.subscription-key", "key-004");
    }
  }
}
