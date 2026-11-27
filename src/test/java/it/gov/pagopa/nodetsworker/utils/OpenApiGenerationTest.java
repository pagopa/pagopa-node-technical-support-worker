package it.gov.pagopa.nodetsworker.utils;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

@QuarkusTest
class OpenApiGenerationTest {
    @Test
    void generateOpenApi() throws Exception {
        CommonUtilsTest.generateOpenApi("openapi.json");
        Assertions.assertTrue(new File("openapi/openapi.json").isFile());
    }
}
