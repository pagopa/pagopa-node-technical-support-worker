package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import it.gov.pagopa.nodetsworker.observability.monitoring.NodoMonitoringClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

/** Default test config has no subscription key: no upstream call is attempted. */
@QuarkusTest
class CacheVersionUnconfiguredTest {
  @InjectMock @RestClient NodoMonitoringClient client;

  @Test
  void missingSubscriptionKeyIsReportedPerNode() {
    given().when().get("/api/nodo-monitoring/cache/version").then()
        .statusCode(200)
        .body("nodes.outcome", contains("UNCONFIGURED", "UNCONFIGURED"))
        .body("nodes.target", contains("NDP001DEV", "NDP004IT"));
    verify(client, never()).cacheVersion(anyString(), anyString());
  }
}
