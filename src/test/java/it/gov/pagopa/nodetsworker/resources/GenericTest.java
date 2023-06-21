package it.gov.pagopa.nodetsworker.resources;


import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.MediaType;

import static io.restassured.RestAssured.given;

@QuarkusTest
class GenericTest {

    @Test
    public void info(){
        given()
                .when().get("/info")
                .then()
                .statusCode(200)
                .contentType(MediaType.APPLICATION_JSON);
    }
}
