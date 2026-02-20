package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.repository.PositionPaymentSnapshotReader;
import it.gov.pagopa.nodetsworker.repository.qualifiers.DefaultNode;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.mockito.Mockito.*;

@QuarkusTest
class NodeSnapshotRouterTest {

    @Inject
    NodeSnapshotRouter router;

    @InjectMock
    @DefaultNode
    PositionPaymentSnapshotReader defaultReader;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "NDP003PROD", "NDP999PROD"})
    void resolve_shouldUseDefaultReader_forAnyServiceIdentifier(String serviceIdentifier) {

        when(defaultReader.findByPaymentToken("t")).thenReturn(Optional.empty());

        PositionPaymentSnapshotReader resolved = router.resolve(serviceIdentifier);
        resolved.findByPaymentToken("t");

        // assert: if resolved is the default reader, then the call to findByPaymentToken should be executed by the default reader mock
        verify(defaultReader, times(1)).findByPaymentToken("t");
        verifyNoMoreInteractions(defaultReader);
    }
}
