package it.gov.pagopa.nodetsworker.service;

import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.PositionPaymentSnapshotDto;
import it.gov.pagopa.nodetsworker.repository.PositionPaymentSnapshotReader;
import it.gov.pagopa.nodetsworker.repository.models.PositionPaymentSSEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class PositionPaymentSnapshotServiceTest {

    @Inject
    PositionPaymentSnapshotService service;

    @InjectMock
    NodeSnapshotRouter router;

    PositionPaymentSnapshotReader reader = mock(PositionPaymentSnapshotReader.class);

    @Test
    void getByPaymentToken_shouldReturnDto_andUseRouterWithServiceIdentifier() {
        String token = "9ab5981f69744efb81f4ff67444e4d58";
        String sid = "NDP003PROD";

        when(router.resolve(sid)).thenReturn(reader);

        PositionPaymentSSEntity entity = PositionPaymentSSEntity.builder()
                .id(1680001L)
                .paFiscalCode("00493410583")
                .noticeId("396000000012231550")
                .creditorReferenceId("96000000012231550")
                .paymentToken(token)
                .status("PAID_NORPT")
                .insertedTimestamp(Instant.parse("2023-04-20T13:28:55.314Z"))
                .updatedTimestamp(Instant.parse("2023-04-20T13:28:59.327Z"))
                .fkPositionPayment(1660001L)
                .insertedBy("activatePaymentNotice")
                .updatedBY("sendPaymentOutcome")
                .build();

        when(reader.findByPaymentToken(token)).thenReturn(Optional.of(entity));

        PositionPaymentSnapshotDto dto = service.getByPaymentToken(token, sid);

        // verify interactions
        verify(router, times(1)).resolve(sid);
        verify(reader, times(1)).findByPaymentToken(token);

        // verify mapping
        assertEquals(1680001L, dto.getId());
        assertEquals("00493410583", dto.getPaFiscalCode());
        assertEquals("396000000012231550", dto.getNoticeId());
        assertEquals("96000000012231550", dto.getCreditorReferenceId());
        assertEquals(token, dto.getPaymentToken());
        assertEquals("PAID_NORPT", dto.getStatus());
        assertEquals(1660001L, dto.getFkPositionPayment());
        assertEquals("activatePaymentNotice", dto.getInsertedBy());
        assertEquals("sendPaymentOutcome", dto.getUpdatedBy());
        assertNotNull(dto.getInsertedTimestamp());
        assertNotNull(dto.getUpdatedTimestamp());
    }

    @Test
    void getByPaymentToken_shouldThrowAppException_whenTokenNotFound() {
        String token = "missing-token";
        String sid = "NDP003PROD";

        when(router.resolve(sid)).thenReturn(reader);
        when(reader.findByPaymentToken(token)).thenReturn(Optional.empty());

        assertThrows(AppException.class, () -> service.getByPaymentToken(token, sid));

        verify(router, times(1)).resolve(sid);
        verify(reader, times(1)).findByPaymentToken(token);
    }
}