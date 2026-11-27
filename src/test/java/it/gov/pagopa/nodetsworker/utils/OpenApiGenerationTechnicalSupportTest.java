package it.gov.pagopa.nodetsworker.utils;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import it.gov.pagopa.nodetsworker.utils.profiles.TechnicalSupportProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

@QuarkusTest
@TestProfile(TechnicalSupportProfile.class)
class OpenApiGenerationTechnicalSupportTest {
    @Test
    void generateOpenApi() throws Exception {
        CommonUtilsTest.generateOpenApi("nodoTechnicalSupport.openapi.json");
        Assertions.assertTrue(new File("openapi/nodoTechnicalSupport.openapi.json").isFile());
    }
}
