package it.gov.pagopa.nodetsworker.service;

import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeSnapshotRouterMissingNdp001MappingStartupIT {

    private static final String INVALID_MAPPING_MESSAGE =
            "Snapshot datasource service identifiers must be non-blank and distinct";

    @RegisterExtension
    static final QuarkusUnitTest quarkus = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(NodeSnapshotRouter.class))
            .overrideConfigKey("snapshot.online.nodes.online-ndp001.service-identifier", " ")
            .overrideConfigKey("snapshot.online.nodes.online-ndp004.service-identifier", "ndp004prod")
            .overrideConfigKey("quarkus.datasource.online-ndp001.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.online-ndp001.jdbc.url", "jdbc:h2:mem:online-ndp001")
            .overrideConfigKey("quarkus.datasource.online-ndp004.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.online-ndp004.jdbc.url", "jdbc:h2:mem:online-ndp004")
            .assertException(NodeSnapshotRouterMissingNdp001MappingStartupIT::assertStartupAborted);

    @Test
    void missingNdp001MappingPreventsQuarkusStartup() {
    }

    private static void assertStartupAborted(Throwable failure) {
        Throwable cause = failure;
        while (cause != null && !(cause instanceof IllegalStateException
                && INVALID_MAPPING_MESSAGE.equals(cause.getMessage()))) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof IllegalStateException,
                "Quarkus startup should fail because the router rejects the missing NDP001 mapping");
    }
}
