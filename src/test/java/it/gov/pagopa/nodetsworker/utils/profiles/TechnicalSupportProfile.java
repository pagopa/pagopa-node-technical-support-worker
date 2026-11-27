package it.gov.pagopa.nodetsworker.utils.profiles;

import io.quarkus.test.junit.QuarkusTestProfile;

public class TechnicalSupportProfile implements QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
        // "openapi" supplies the local config (H2, placeholders), "technical_support" selects the API group.
        return "openapi,technical_support";
    }
}
