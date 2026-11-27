package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.datasource.DatasourceRegistry;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.repository.PositionPaymentSnapshotReader;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    @Test
    void resolve_shouldAnswerUnavailableForDisabledNodeWithoutLookupOrFallback() {
        List<String> lookups = new ArrayList<>();
        DatasourceRegistry registry = new DatasourceRegistry(
                DatasourceRegistry.ONLINE_NDP004::equals,
                name -> {
                    lookups.add(name);
                    return ndp004prodDataSource;
                },
                Set.of(DatasourceRegistry.ONLINE_NDP001, DatasourceRegistry.ONLINE_NDP004),
                key -> null);
        NodeSnapshotRouter router = new NodeSnapshotRouter("ndp001", "ndp004prod", registry);

        AppException reader = assertThrows(AppException.class, () -> router.resolve("ndp001"));
        AppException dataSource = assertThrows(AppException.class, () -> router.resolveDataSource("NDP001"));

        assertEquals(AppErrorCodeMessageEnum.DATASOURCE_UNAVAILABLE, reader.getCodeMessage());
        assertEquals(AppErrorCodeMessageEnum.DATASOURCE_UNAVAILABLE, dataSource.getCodeMessage());
        assertTrue(lookups.isEmpty());
        assertSame(ndp004prodDataSource, router.resolveDataSource("ndp004prod"));
        assertEquals(List.of(DatasourceRegistry.ONLINE_NDP004), lookups);
        verifyNoInteractions(onlineDataSource, ndp004prodDataSource);
    }

    private NodeSnapshotRouter router(String onlineId, String ndp004prodId) {
        DatasourceRegistry registry = new DatasourceRegistry(
                name -> true,
                name -> DatasourceRegistry.ONLINE_NDP001.equals(name) ? onlineDataSource : ndp004prodDataSource,
                Set.of(DatasourceRegistry.ONLINE_NDP001, DatasourceRegistry.ONLINE_NDP004),
                key -> null);
        return new NodeSnapshotRouter(onlineId, ndp004prodId, registry);
    }
}
