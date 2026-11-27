package it.gov.pagopa.nodetsworker.datasource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every datasource disabled through its {@code *_DATASOURCE_ENABLED} flag, with connection settings that
 * reference unset environment variables: the application must start without any pool or persistence unit,
 * stay ready and answer 503 on every route backed by a disabled datasource.
 */
@QuarkusTest
@TestProfile(AllDatasourcesDisabledTest.AllDisabledProfile.class)
class AllDatasourcesDisabledTest {

    static final String[] DATASOURCES = {
            "offline-ndp004", "offline-ndp001", "online-ndp001", "online-ndp004", "observability"};

    @Test
    void startsWithoutCreatingPoolsOrPersistenceUnits() throws Exception {
        assertEquals(Set.of(), DatasourceProbe.createdPools());
        assertEquals(Set.of("<default>", "offline-ndp001", "observability"),
                DatasourceProbe.deactivatedPersistenceUnits());
    }

    @Test
    void readinessIgnoresDisabledDatasources() throws Exception {
        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("status", equalTo("UP"));
        assertEquals(Set.of(), DatasourceProbe.createdPools());
    }

    @Test
    void offlineSnapshotOnDisabledNodesAnswers503WithoutFallback() {
        for (String serviceIdentifier : new String[]{null, "NDP004", "NDP001"}) {
            var request = given();
            if (serviceIdentifier != null) {
                request.queryParam("serviceIdentifier", serviceIdentifier);
            }
            request.when().get("/snapshot/organizations/77777777777")
                    .then().statusCode(503)
                    .body("status", equalTo(503));
        }
    }

    @Test
    void onlineSnapshotOnDisabledNodesAnswers503() {
        for (String serviceIdentifier : new String[]{"ndp001", "ndp004prod"}) {
            given().queryParam("serviceIdentifier", serviceIdentifier)
                    .when().get("/paymentToken/{paymentToken}", "token")
                    .then().statusCode(503)
                    .body("status", equalTo(503));
        }
    }

    @Test
    void unknownNodeIsStillABadRequest() {
        given().queryParam("serviceIdentifier", "NDP999")
                .when().get("/paymentToken/{paymentToken}", "token")
                .then().statusCode(400);
    }

    @Test
    void observabilityApiAnswers503WhenItsDatasourceIsDisabled() {
        for (String path : new String[]{"/api/overview", "/api/catalog", "/api/payments", "/api/freshness"}) {
            given().when().get(path)
                    .then().statusCode(503)
                    .body("code", equalTo("DATASOURCE_UNAVAILABLE"));
        }
        given().contentType("application/json").body("{}")
                .when().post("/internal/observability/sync/payment")
                .then().statusCode(503)
                .body("code", equalTo("DATASOURCE_UNAVAILABLE"));
    }

    @Test
    void observabilityRoutesWithoutDatabaseKeepWorking() {
        given().when().get("/api/serviceIdentifiers").then().statusCode(200);
    }

    public static class AllDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> config = new HashMap<>();
            for (String name : DATASOURCES) {
                config.put("%test.quarkus.datasource." + name + ".active", "false");
                config.put("%test.quarkus.datasource." + name + ".jdbc.url", "${UNSET_" + name.replace('-', '_') + "_URL}");
                config.put("%test.quarkus.datasource." + name + ".username", "${UNSET_" + name.replace('-', '_') + "_USER}");
            }
            config.put("quarkus.hibernate-orm.database.default-schema", "${UNSET_OFFLINE_NDP004_SCHEMA}");
            config.put("%test.quarkus.hibernate-orm.offline-ndp001.database.default-schema",
                    "${UNSET_OFFLINE_NDP001_SCHEMA}");
            return config;
        }
    }
}
