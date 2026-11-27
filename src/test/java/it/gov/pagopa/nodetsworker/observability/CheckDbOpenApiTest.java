package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CheckDbOpenApiTest {
  @Inject ObjectMapper mapper;

  @Test
  void publishedContractMatchesTheRealEndpointAndItsRequiredNullableSnapshotFields() throws Exception {
    JsonNode generated = mapper.readTree(given().queryParam("format", "json").get("/q/openapi")
        .then().statusCode(200).extract().asString());
    JsonNode published = mapper.readTree(Path.of("openapi/openapi.json").toFile());
    String path = "/api/attempts/{attemptId}/check-db";
    assertEquals(generated.path("paths").path(path), published.path("paths").path(path));
    for (String name : List.of("DbCheck", "DbCheckObservedStates", "DbCheckDatabaseStates",
        "LogicalState", "AttemptState", "ErrorDto")) {
      assertEquals(generated.at("/components/schemas").path(name),
          published.at("/components/schemas").path(name), name + " is stale");
    }
    JsonNode schema = generated.at("/components/schemas/DbCheck");
    assertEquals(9, schema.path("required").size());
    assertEquals("NODO_ONLINE", schema.at("/properties/source/enum/0").asText());
    for (String state : List.of("logicalState", "attemptState")) {
      assertTrue(generated.at("/components/schemas/DbCheckDatabaseStates/properties")
          .path(state).path("nullable").asBoolean());
    }
    assertEquals(5, generated.at("/components/schemas/LogicalState/enum").size());
    assertEquals(18, generated.at("/components/schemas/AttemptState/enum").size());
    assertFalse(schema.path("properties").has("paymentToken"));
  }
}
