package it.gov.pagopa.nodetsworker.utils.profiles;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ObservabilityProfile implements QuarkusTestProfile {
    @Override
    public String getConfigProfile() {
        // "openapi" supplies the local config (H2, placeholders), "observability" selects the API group.
        return "openapi,observability";
    }
}
