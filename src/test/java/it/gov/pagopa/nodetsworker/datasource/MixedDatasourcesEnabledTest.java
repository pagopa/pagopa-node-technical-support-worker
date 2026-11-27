package it.gov.pagopa.nodetsworker.datasource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Only offline-ndp004 and online-ndp001 enabled: they work, the other nodes answer 503 without fallback. */
@QuarkusTest
@TestProfile(MixedDatasourcesEnabledTest.MixedProfile.class)
class MixedDatasourcesEnabledTest {

    @Test
    void onlyEnabledDatasourcesGetAPoolAndAPersistenceUnit() throws Exception {
        given().when().get("/snapshot/organizations/77777777777").then().statusCode(200);
        given().queryParam("serviceIdentifier", "ndp001")
                .when().get("/paymentToken/{paymentToken}", "token")
                .then().statusCode(not(503));

        Set<String> pools = DatasourceProbe.createdPools();
        assertEquals(Set.of("offline-ndp004", "online-ndp001"), pools);
        assertEquals(Set.of("offline-ndp001", "observability"), DatasourceProbe.deactivatedPersistenceUnits());
    }

    @Test
    void disabledNodesAnswer503() {
        given().queryParam("serviceIdentifier", "NDP001")
                .when().get("/snapshot/organizations/77777777777")
                .then().statusCode(503);
        given().queryParam("serviceIdentifier", "ndp004prod")
                .when().get("/paymentToken/{paymentToken}", "token")
                .then().statusCode(503);
        given().when().get("/api/overview").then().statusCode(503)
                .body("code", equalTo("DATASOURCE_UNAVAILABLE"));
    }

    @Test
    void readinessChecksOnlyEnabledNonExcludedDatasources() {
        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.find { it.name == 'Database connections health check' }.data", notNullValue())
                .body("checks.find { it.name == 'Database connections health check' }.data", hasKey("offline-ndp004"))
                .body("checks.find { it.name == 'Database connections health check' }.data", hasKey("online-ndp001"))
                .body("checks.find { it.name == 'Database connections health check' }.data",
                        not(hasKey("offline-ndp001")));
    }

    public static class MixedProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "%test.quarkus.datasource.offline-ndp004.active", "true",
                    "%test.quarkus.datasource.online-ndp001.active", "true",
                    "%test.quarkus.datasource.offline-ndp001.active", "false",
                    "%test.quarkus.datasource.online-ndp004.active", "false",
                    "%test.quarkus.datasource.observability.active", "false");
        }
    }
}
