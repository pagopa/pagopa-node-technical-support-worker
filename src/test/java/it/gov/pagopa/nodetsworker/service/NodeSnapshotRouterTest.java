package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.repository.PositionPaymentSnapshotReader;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NodeSnapshotRouterTest {

    private final DataSource onlineDataSource = mock(DataSource.class);
    private final DataSource ndp004prodDataSource = mock(DataSource.class);

    @Test
    void resolve_shouldSelectReaderByTrimmedCaseInsensitiveIdentifier() {
        NodeSnapshotRouter router = router("ndp001", "ndp004prod");

        PositionPaymentSnapshotReader ndp001 = router.resolve(" NDP001 ");
        PositionPaymentSnapshotReader ndp004prod = router.resolve("ndp004prod");

        assertSame(ndp001, router.resolve("ndp001"));
        assertSame(ndp004prod, router.resolve(" NDP004PROD "));
        assertNotSame(ndp001, ndp004prod);
    }

    @Test
    void resolve_shouldUseConfiguredIdentifiers() {
        NodeSnapshotRouter router = router(" NDP001UAT ", "NDP004UAT");

        PositionPaymentSnapshotReader ndp001 = router.resolve("ndp001uat");
        PositionPaymentSnapshotReader ndp004 = router.resolve(" ndp004uat ");

        assertSame(ndp001, router.resolve("NDP001UAT"));
        assertSame(ndp004, router.resolve("NDP004UAT"));
        assertNotSame(ndp001, ndp004);
    }

    @Test
    void resolve_shouldRejectMissingBlankAndUnknownIdentifiersWithoutAcquiringConnections() {
        NodeSnapshotRouter router = router("ndp001", "ndp004prod");

        for (String serviceIdentifier : new String[]{null, "", "   ", "NDP999"}) {
            AppException exception = assertThrows(
                    AppException.class, () -> router.resolve(serviceIdentifier));
            assertEquals(AppErrorCodeMessageEnum.BAD_REQUEST, exception.getCodeMessage());
        }

        verifyNoInteractions(onlineDataSource, ndp004prodDataSource);
    }

    @Test
    void resolveDataSource_shouldReuseConfiguredRoutingWithoutOpeningConnections() {
        NodeSnapshotRouter router = router(" NDP001UAT ", "NDP004UAT");

        assertSame(onlineDataSource, router.resolveDataSource("ndp001uat"));
        assertSame(ndp004prodDataSource, router.resolveDataSource(" NDP004UAT "));
        for (String identifier : new String[]{null, "", "unknown"}) {
            assertThrows(AppException.class, () -> router.resolveDataSource(identifier));
        }
        verifyNoInteractions(onlineDataSource, ndp004prodDataSource);
    }

    @Test
    void constructor_shouldRejectMissingAssociations() {
        assertThrows(IllegalStateException.class, () -> router("", "ndp004prod"));
        assertThrows(IllegalStateException.class, () -> router("ndp001", ""));
    }

    @Test
    void constructor_shouldRejectIdentifiersThatNormalizeToDuplicates() {
        assertThrows(IllegalStateException.class, () -> router("NDP001", " ndp001 "));
    }

    private NodeSnapshotRouter router(String onlineId, String ndp004prodId) {
        return new NodeSnapshotRouter(onlineId, ndp004prodId, onlineDataSource, ndp004prodDataSource);
    }
}
