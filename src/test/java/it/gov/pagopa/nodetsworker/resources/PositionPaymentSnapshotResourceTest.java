package it.gov.pagopa.nodetsworker.resources;

import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.models.PositionPaymentSnapshotDto;
import it.gov.pagopa.nodetsworker.service.PositionPaymentSnapshotService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

@QuarkusTest
class PositionPaymentSnapshotResourceTest {

    @InjectMock
    PositionPaymentSnapshotService service;

    @Test
    void getByPaymentToken_shouldReturn200_andJsonBody() {
        String token = "9ab5981f69744efb81f4ff67444e4d58";
        String sid = "NDP003PROD";

        PositionPaymentSnapshotDto dto = PositionPaymentSnapshotDto.builder()
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
                .updatedBy("sendPaymentOutcome")
                .build();

        when(service.getByPaymentToken(token, sid)).thenReturn(dto);

        given()
            .queryParam("serviceIdentifier", sid)
        .when()
            .get("/paymentToken/{paymentToken}", token)
        .then()
            .statusCode(200)
            .body("id", is(1680001))
            .body("pa_fiscal_code", is("00493410583"))
            .body("notice_id", is("396000000012231550"))
            .body("creditor_reference_id", is("96000000012231550"))
            .body("payment_token", is(token))
            .body("status", is("PAID_NORPT"))
            .body("fk_position_payment", is(1660001))
            .body("inserted_by", is("activatePaymentNotice"))
            .body("updated_by", is("sendPaymentOutcome"));

        verify(service, times(1)).getByPaymentToken(token, sid);
    }
    
    @Test
    void getByPaymentToken_shouldReturn404_whenNotFound() {
        String token = "missing-token";
        String sid = "NDP003PROD";

        when(service.getByPaymentToken(token, sid))
                .thenThrow(new AppException(AppErrorCodeMessageEnum.NOT_FOUND, "No snapshot row found"));

        given()
            .queryParam("serviceIdentifier", sid)
        .when()
            .get("/paymentToken/{paymentToken}", token)
        .then()
            .statusCode(404);
    }
    
    @Test
    void getByPaymentToken_shouldWork_withoutServiceIdentifier() {
        String token = "9ab5981f69744efb81f4ff67444e4d58";

        PositionPaymentSnapshotDto dto = PositionPaymentSnapshotDto.builder()
                .id(1L)
                .paFiscalCode("00493410583")
                .noticeId("396000000012231550")
                .creditorReferenceId("96000000012231550")
                .paymentToken(token)
                .status("PAID_NORPT")
                .build();

        when(service.getByPaymentToken(token, null)).thenReturn(dto);

        given()
        .when()
            .get("/paymentToken/{paymentToken}", token)
        .then()
            .statusCode(200)
            .body("payment_token", is(token));

        verify(service, times(1)).getByPaymentToken(token, null);
    }
}