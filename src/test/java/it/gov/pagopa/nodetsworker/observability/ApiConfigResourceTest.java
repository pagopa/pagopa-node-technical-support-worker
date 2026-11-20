package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.observability.dto.MaintenancesDto;
import it.gov.pagopa.nodetsworker.observability.apiconfig.MaintenanceCache;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ApiConfigResourceTest {
  private static final String PATH = "/api/apiconfig/maintenances";

  @InjectMock MaintenanceCache cache;

  @Test
  void returnsTheCachedMaintenances() {
    when(cache.current()).thenReturn(Optional.of(new MaintenancesDto(
        "2026-11-04T23:00:00.000Z", "2026-11-04T23:00:00.000Z", "2026-11-11T23:00:00.000Z", false,
        List.of(new MaintenancesDto.Maintenance("B1", "One", "2026-11-05T09:00:00.000Z", "2026-11-05T10:00:00.000Z",
            List.of("B1_01"), List.of(new MaintenancesDto.Creditor("Ente", "00000000001")))))));

    given().when().get(PATH).then()
        .statusCode(200)
        .header("Cache-Control", containsString("no-store"))
        .body("stale", equalTo(false))
        .body("items[0].brokerCode", equalTo("B1"))
        .body("items[0].stations", contains("B1_01"))
        .body("items[0].creditorInstitutions[0].code", equalTo("00000000001"));
  }

  @Test
  void answers503WhenNoDataIsAvailable() {
    when(cache.current()).thenReturn(Optional.empty());

    given().when().get(PATH).then().statusCode(503).body("code", equalTo("MAINTENANCE_UNAVAILABLE"));
  }

  @Test
  void rejectsQueryParameters() {
    given().when().get(PATH + "?unexpected=value").then().statusCode(400).body("code", equalTo("INVALID_REQUEST"));
  }
}
