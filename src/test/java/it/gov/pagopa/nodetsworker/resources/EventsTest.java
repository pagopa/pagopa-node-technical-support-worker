package it.gov.pagopa.nodetsworker.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import it.gov.pagopa.nodetsworker.exceptions.AppErrorCodeMessageEnum;
import it.gov.pagopa.nodetsworker.exceptions.AppException;
import it.gov.pagopa.nodetsworker.service.WorkerService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static it.gov.pagopa.nodetsworker.util.AppConstantTestHelper.*;
import static it.gov.pagopa.nodetsworker.util.AppConstantTestHelper.newNegBiz;
import static org.mockito.Mockito.when;

@QuarkusTest
class EventsTest {

  @InjectMock
  WorkerService workerService;

  @BeforeEach
  void setUp() {
    when(workerService.getNegativeBizEventById("test_id_ok"))
            .thenReturn(newNegBiz("testPA", "3400000000", "400000000", false));
    when(workerService.getNegativeBizEventById("test_id_ko"))
            .thenThrow(new AppException(AppErrorCodeMessageEnum.NOT_FOUND));
  }

  @SneakyThrows
  @Test
  @DisplayName("KO Pa Fiscal Code path null")
  void getNegativeBizEvent_OK() {
    StringBuilder url = new StringBuilder(NEG_BIZ_EVENT_ID_PATH.formatted("test_id_ok"));

    given()
            .header(HEADER)
            .when()
            .get(url.toString())
            .then()
            .statusCode(200);
  }

  @SneakyThrows
  @Test
  @DisplayName("KO page param string value")
  void getNegativeBizEvent_KO() {
    StringBuilder url = new StringBuilder(NEG_BIZ_EVENT_ID_PATH.formatted("test_id_ko"));

    given()
            .header(HEADER)
            .when()
            .get(url.toString())
            .then()
            .statusCode(404);
  }

}
