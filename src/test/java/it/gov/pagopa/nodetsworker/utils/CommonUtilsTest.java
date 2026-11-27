package it.gov.pagopa.nodetsworker.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.restassured.RestAssured.given;

class CommonUtilsTest {

    public static void generateOpenApi(String filename) throws Exception {
        String responseString =
                given()
                        .when().get("/q/openapi?format=json")
                        .then()
                        .statusCode(200)
                        .contentType("application/json")
                        .extract()
                        .asString();

        ObjectMapper objectMapper = new ObjectMapper();
        Object swagger = objectMapper.readValue(responseString, Object.class);
        String formatted = objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(swagger);

        Path basePath = Paths.get("openapi/");
        Files.createDirectories(basePath);
        Files.write(basePath.resolve(filename), formatted.getBytes());
    }
}

