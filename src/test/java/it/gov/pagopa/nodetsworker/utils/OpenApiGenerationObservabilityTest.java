package it.gov.pagopa.nodetsworker.utils;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import it.gov.pagopa.nodetsworker.utils.profiles.ObservabilityProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

@QuarkusTest
@TestProfile(ObservabilityProfile.class)
class OpenApiGenerationObservabilityTest {
    @Test
    void generateOpenApi() throws Exception {
        CommonUtilsTest.generateOpenApi("observability_openapi.json");
        Assertions.assertTrue(new File("openapi/observability_openapi.json").isFile());
    }
}
