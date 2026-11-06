package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ReDisabledResourceTest {
  @Test
  void isDisabledByDefaultAndAllErrorsAreNoStore() {
    given().contentType("application/json")
        .body("{\"from\":\"2026-10-23T10:00:00Z\",\"to\":\"2026-10-23T11:00:00Z\"}")
        .post("/api/attempts/missing/re-events")
        .then().statusCode(503).header("Cache-Control", "no-store")
        .body("code", equalTo("RE_UNAVAILABLE"));
  }
}
