package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ServiceIdentifiersResourceTest.IdentifierConfigProfile.class)
class ServiceIdentifiersResourceTest {

  @Test
  void returnsConfiguredIdentifiersInStableOrder() {
    String[] identifiers = given().when().get("/api/serviceIdentifiers").then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("size()", equalTo(2))
        .extract().as(String[].class);

    assertArrayEquals(new String[] {"ndp001-test", "ndp004-test"}, identifiers);
  }

  @Test
  void rejectsQueryParameters() {
    given().when().get("/api/serviceIdentifiers?unexpected=value").then()
        .statusCode(400)
        .body("code", equalTo("INVALID_REQUEST"));
  }

  public static class IdentifierConfigProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "snapshot.online.nodes.online-ndp001.service-identifier", "ndp001-test",
          "snapshot.online.nodes.online-ndp004.service-identifier", "ndp004-test");
    }
  }
}
