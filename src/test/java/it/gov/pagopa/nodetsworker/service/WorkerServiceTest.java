package it.gov.pagopa.nodetsworker.service;

import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import it.gov.pagopa.nodetsworker.models.enums.PaymentStatus;
import it.gov.pagopa.nodetsworker.repository.CosmosBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosNegBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosVerifyKOEventClient;
import it.gov.pagopa.nodetsworker.repository.models.*;
import it.gov.pagopa.nodetsworker.resources.response.PaymentsResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class WorkerServiceTest {

    @Inject
    WorkerService workerService;

    @InjectMock
    CosmosBizEventClient positiveBizClient;

    @InjectMock
    CosmosNegBizEventClient negativeBizClient;

    @InjectMock
    CosmosVerifyKOEventClient verifyKOEventClient;

    @BeforeEach
    void resetMocks() {
        reset(positiveBizClient, negativeBizClient, verifyKOEventClient);
    }

    @Test
    void getInfoByNoticeNumber_shouldMapVerifyKoToFailed_andNegativeStatuses() {
        String pa = "80007580279";
        String notice = "301130001425371985";
        LocalDate dateFrom = LocalDate.of(2026, 1, 21);
        LocalDate dateTo   = LocalDate.of(2026, 1, 28);

        VerifyKOEvent vko = mockVerifyKoEvent("vko-1", "2026-01-22T10:00:00.000Z", pa, notice, "01130001425371985");
        PositiveBizEvent pbe = mockPositiveBizEvent("pbe-1", "2026-01-23T10:00:00.000Z", pa, notice, "01130001425371985", "token-ok-1");

        NegativeBizEvent nbeFailed    = mockNegativeBizEvent("nbe-1", "sendPaymentOutcomeV2", "2026-01-24T10:00:00.000Z", pa, notice, "01130001425371985", "token-ko-1", true);
        NegativeBizEvent nbeUnknown   = mockNegativeBizEvent("nbe-2", "nodoInviaRT",          "2026-01-25T10:00:00.000Z", pa, notice, "01130001425371985", "token-ko-2", true);
        NegativeBizEvent nbeCancelled = mockNegativeBizEvent("nbe-3", "mod3CancelV2",         "2026-01-26T10:00:00.000Z", pa, notice, "01130001425371985", "token-ko-3", true);

        CosmosPagedIterable<VerifyKOEvent> vkoPaged = cosmosIterableOf(List.of(vko));
        CosmosPagedIterable<PositiveBizEvent> pbePaged = cosmosIterableOf(List.of(pbe));
        CosmosPagedIterable<NegativeBizEvent> nbePaged = cosmosIterableOf(List.of(nbeFailed, nbeUnknown, nbeCancelled));

        when(verifyKOEventClient.findEventsByCiAndNN(eq(pa), eq(notice), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(vkoPaged);

        when(positiveBizClient.findEventsByCiAndNNAndToken(eq(pa), eq(notice), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(pbePaged);

        when(negativeBizClient.findEventsByCiAndNNAndToken(eq(pa), eq(notice), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(nbePaged);

        PaymentsResponse resp = workerService.getInfoByNoticeNumber(pa, notice, Optional.empty(), dateFrom, dateTo);

        assertEquals(5, resp.getCount());
        assertEquals(5, resp.getPayments().size());

        List<PaymentInfo> list = resp.getPayments();

        // order by insertedTimestamp
        assertEquals("vko-1", list.get(0).getVerifyKoEvtId());
        assertEquals("pbe-1", list.get(1).getPositiveBizEvtId());
        assertEquals("nbe-1", list.get(2).getNegativeBizEvtId());
        assertEquals("nbe-2", list.get(3).getNegativeBizEvtId());
        assertEquals("nbe-3", list.get(4).getNegativeBizEvtId());

        // status mapping
        assertEquals(PaymentStatus.FAILED,    list.get(0).getStatus()); // VerifyKO
        assertEquals(PaymentStatus.COMPLETED, list.get(1).getStatus()); // Positive
        assertEquals(PaymentStatus.FAILED,    list.get(2).getStatus()); // sendPaymentOutcomeV2
        assertEquals(PaymentStatus.UNKNOWN,   list.get(3).getStatus()); // nodoInviaRT
        assertEquals(PaymentStatus.CANCELLED, list.get(4).getStatus()); // default

        // outcome
        assertEquals("KO", list.get(0).getOutcome());
        assertEquals("OK", list.get(1).getOutcome());
        assertEquals("KO", list.get(2).getOutcome());
        assertEquals("KO", list.get(3).getOutcome());
        assertEquals("KO", list.get(4).getOutcome());
    }

    @Test
    void negativeEvent_outcomeShouldBeNull_whenReAwakableIsFalseOrNull() {
        String pa = "80007580279";
        String notice = "301130001425371985";
        LocalDate dateFrom = LocalDate.of(2026, 1, 21);
        LocalDate dateTo   = LocalDate.of(2026, 1, 28);

        NegativeBizEvent nbeNull = mockNegativeBizEvent("nbe-1", "mod3CancelV2", "2026-01-24T10:00:00.000Z",
                pa, notice, "01130001425371985", "token-1", null);
        NegativeBizEvent nbeFalse = mockNegativeBizEvent("nbe-2", "mod3CancelV2", "2026-01-25T10:00:00.000Z",
                pa, notice, "01130001425371985", "token-2", false);

        CosmosPagedIterable<VerifyKOEvent> emptyVko = cosmosIterableOf(List.of());
        CosmosPagedIterable<PositiveBizEvent> emptyPbe = cosmosIterableOf(List.of());
        CosmosPagedIterable<NegativeBizEvent> negPaged = cosmosIterableOf(List.of(nbeNull, nbeFalse));

        when(verifyKOEventClient.findEventsByCiAndNN(anyString(), anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(emptyVko);
        when(positiveBizClient.findEventsByCiAndNNAndToken(anyString(), anyString(), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(emptyPbe);
        when(negativeBizClient.findEventsByCiAndNNAndToken(anyString(), anyString(), any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(negPaged);

        PaymentsResponse resp = workerService.getInfoByNoticeNumber(pa, notice, Optional.empty(), dateFrom, dateTo);

        assertEquals(2, resp.getPayments().size());
        assertNull(resp.getPayments().get(0).getOutcome());
        assertNull(resp.getPayments().get(1).getOutcome());
    }

    @Test
    void getNegativeBizEventById_shouldReturnEvent_whenPresent() {
        NegativeBizEvent nbe = mock(NegativeBizEvent.class);

        CosmosPagedIterable<NegativeBizEvent> paged = cosmosIterableOf(List.of(nbe));
        when(negativeBizClient.getNegativeBizEventById("nbe-1")).thenReturn(paged);

        NegativeBizEvent res = workerService.getNegativeBizEventById("nbe-1");
        assertSame(nbe, res);
    }

    @Test
    void getNegativeBizEventById_shouldThrowNotFound_whenMissing() {
        CosmosPagedIterable<NegativeBizEvent> emptyPaged = cosmosIterableOf(List.of());
        when(negativeBizClient.getNegativeBizEventById("missing")).thenReturn(emptyPaged);

        assertThrows(AppException.class, () -> workerService.getNegativeBizEventById("missing"));
    }

    // -----------------------
    // Helpers
    // -----------------------

    private static <T> CosmosPagedIterable<T> cosmosIterableOf(List<T> items) {
        @SuppressWarnings("unchecked")
        CosmosPagedIterable<T> paged = mock(CosmosPagedIterable.class);
        doAnswer(inv -> items.stream()).when(paged).stream();
        return paged;
    }

    private static VerifyKOEvent mockVerifyKoEvent(String id, String dateTimeIso, String pa, String noticeNumber, String iuv) {
        return VerifyKOEvent.builder()
                .id(id)
                .serviceIdentifier("NDP003PROD")
                .psp(Psp.builder()
                        .idPsp("ABI03069")
                        .idBrokerPsp("97249640588")
                        .idChannel("97249640588_01")
                        .build())
                .creditor(Creditor.builder()
                        .idPA(pa)
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .noticeNumber(noticeNumber)
                        .iuv(iuv)
                        .build())
                .faultBean(Fault.builder()
                        .faultCode("PPT_VERIFY_KO")
                        .description("desc")
                        .dateTime(dateTimeIso)
                        .timestamp(0L)
                        .build())
                .build();
    }

    private static PositiveBizEvent mockPositiveBizEvent(
            String id,
            String paymentDateTime,
            String pa,
            String noticeNumber,
            String iuv,
            String token) {

        return PositiveBizEvent.builder()
                .id(id)
                .psp(Psp.builder()
                        .idPsp("ABI03069")
                        .idBrokerPsp("97249640588")
                        .idChannel("97249640588_01")
                        .build())
                .creditor(Creditor.builder()
                        .idPA(pa)
                        .idBrokerPA("BROKER_PA")
                        .idStation("STATION_01")
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .noticeNumber(noticeNumber)
                        .iuv(iuv)
                        .build())
                .paymentInfo(
                        it.gov.pagopa.nodetsworker.repository.models.PaymentInfo.builder()
                                .paymentDateTime(paymentDateTime)
                                .paymentToken(token)
                                .amount(null)
                                .fee(null)
                                .primaryCiIncurredFee(null)
                                .paymentMethod(null)
                                .touchpoint(null)
                                .build()
                )
                .properties(Map.of("serviceIdentifier", "NDP003PROD"))
                .build();
    }

    private static NegativeBizEvent mockNegativeBizEvent(
            String id,
            String businessProcess,
            String paymentDateTime,
            String pa,
            String noticeNumber,
            String iuv,
            String token,
            Boolean reAwakable) {

        return NegativeBizEvent.builder()
                .id(id)
                .businessProcess(businessProcess)
                .reAwakable(reAwakable)
                .psp(Psp.builder()
                        .idPsp("ABI03069")
                        .idBrokerPsp("97249640588")
                        .idChannel("97249640588_01")
                        .build())
                .creditor(Creditor.builder()
                        .idPA(pa)
                        .idBrokerPA("BROKER_PA")
                        .idStation("STATION_01")
                        .build())
                .debtorPosition(DebtorPosition.builder()
                        .noticeNumber(noticeNumber)
                        .iuv(iuv)
                        .build())
                .paymentInfo(
                        NegativePaymentInfo.builder()
                                .paymentDateTime(paymentDateTime)
                                .paymentToken(token)
                                .amount(null)
                                .paymentMethod(null)
                                .touchpoint(null)
                                .build()
                )
                .properties(Map.of("serviceIdentifier", "NDP003PROD"))
                .build();
    }
}