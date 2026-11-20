package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;

@QuarkusTest
class OpenApiProjectionContractTest {
  @Inject ObjectMapper mapper;

  @Test
  void generatedSchemaRequiresRecipientSupportAndNullableProvenance() throws Exception {
    JsonNode schemas = mapper.readTree(given().queryParam("format", "json").get("/q/openapi")
        .then().statusCode(200).extract().asString()).at("/components/schemas");
    assertTrue(values(schemas.at("/PaymentDetailDto/required")).contains("recipients"));
    for (String aggregate : List.of("OverviewDto", "CreditorDetailDto")) {
      assertTrue(values(schemas.path(aggregate).path("required")).contains("recipientStates"), aggregate);
    }
    assertTrue(values(schemas.at("/MatricesDto/required")).contains("recipient"));
    assertEquals(List.of("logical", "attempt", "recipient"),
        values(schemas.at("/DurationRowDto/properties/entity/enum")));
    assertEquals(List.of("NOTICE_GENERATED", "NOTICE_SENT", "NOTICE_PENDING", "NOTIFIED"),
        values(schemas.at("/RecipientDto/properties/state/enum")));
    assertEquals(18, schemas.at("/AttemptDto/properties/state/enum").size());
    assertEquals(5, schemas.at("/PaymentDetailDto/properties/state/enum").size());
    assertTrue(schemas.at("/RecipientDto/properties/stazioneRecipient/nullable").asBoolean());
    assertTrue(schemas.at("/TimelineItemDto/properties/transitionAt/nullable").asBoolean());
    assertTrue(schemas.at("/TimelineItemDto/properties/trigger/nullable").asBoolean());
    assertFalse(schemas.at("/RecipientDto/additionalProperties").asBoolean(true));
    assertFalse(schemas.at("/RecipientDto/properties").has("paymentToken"));
    assertTrue(schemas.at("/PaymentChanges/properties/recipientChanges/items/$ref").asText()
        .endsWith("/RecipientChange"));
    assertEquals("string", schemas.at("/PaymentChanges/properties/removedRecipientIds/items/type").asText());
    JsonNode documentedSchemas = mapper.readTree(Path.of("openapi/openapi.json").toFile())
        .at("/components/schemas");
    for (String name : List.of("RecipientDto", "PaymentDetailDto", "TimelineItemDto",
        "OverviewDto", "CreditorDetailDto", "MatricesDto", "DurationRowDto",
        "TransitionTrendDto", "PaymentChanges", "RecipientChange", "AggregateChanges")) {
      assertEquals(schemas.path(name), documentedSchemas.path(name), name + " is stale");
    }
  }

  @Test
  void generatedJsonAndYamlContractsAreEquivalent() throws Exception {
    JsonNode json = mapper.readTree(Path.of("openapi/openapi.json").toFile());
    Yaml parser = new Yaml(new SafeConstructor(new LoaderOptions()) {
      {
        // OpenAPI date examples are strings, not YAML-native Date objects.
        yamlConstructors.put(Tag.TIMESTAMP, yamlConstructors.get(Tag.STR));
      }
    });
    JsonNode yaml = mapper.valueToTree(parser.load(Files.readString(Path.of("openapi/openapi.yaml"))));
    assertEquals(json, yaml);
  }

  private static List<String> values(JsonNode node) {
    List<String> result = new ArrayList<>();
    node.forEach(value -> result.add(value.asText()));
    return result;
  }
}
