package it.gov.pagopa.nodetsworker.resources;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import it.gov.pagopa.nodetsworker.models.ProblemJson;
import it.gov.pagopa.nodetsworker.resources.response.PaymentResponse;
import lombok.SneakyThrows;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static it.gov.pagopa.nodetsworker.util.AppConstantTestHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class SnapshotTest {

  @ConfigProperty(name = "date-range-limit")
  Integer dateRangeLimit;

  @SneakyThrows
  @Test
  @DisplayName("KO Pa Fiscal Code path null")
  void paFiscalCodeNull() {
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(""));

    given()
            .header(HEADER)
            .when()
            .get(url.toString())
            .then()
            .statusCode(404);
  }

  @SneakyThrows
  @Test
  @DisplayName("KO page param string value")
  void pageNumberValueString() {
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));

    url.append("?page=");
    url.append("pageString");

    given()
            .header(HEADER)
            .when()
            .get(url.toString())
            .then()
            .statusCode(404);
  }

  @SneakyThrows
  @Test
  @DisplayName("KO size param string value")
  void sizeValueString() {
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));

    url.append("?size=");
    url.append("sizeString");

    given()
            .header(HEADER)
            .when()
            .get(url.toString())
            .then()
            .statusCode(404);
  }

  @SneakyThrows
  @Test
  @DisplayName("Dates range too wide")
  void datesRangeTooWide() {
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));

    LocalDate dateFrom = LocalDate.of(2023, 1, 30);
    LocalDate dateTo = LocalDate.of(2023, 6, 6);
    url.append("?dateFrom=").append(dateFrom);
    url.append("&dateTo=").append(dateTo);

    ProblemJson res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(400)
                    .extract()
                    .as(ProblemJson.class);

    assertThat(res.getStatus(), equalTo(400));
    assertThat(res.getTitle(), equalTo("NODE_TECH_SUPPORT-0401"));
    assertThat(res.getDetails(), equalTo("Date interval too large,max date difference must be %s days".formatted(dateRangeLimit)));
  }

  @SneakyThrows
  @Test
  @DisplayName("DateTo null")
  void dateToNull() {
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));

    LocalDate dateFrom = LocalDate.of(2023, 1, 30);
    url.append("?dateFrom=").append(dateFrom);

    ProblemJson res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(400)
                    .extract()
                    .as(ProblemJson.class);

    assertThat(res.getStatus(), equalTo(400));
    assertThat(res.getTitle(), equalTo("NODE_TECH_SUPPORT-0400"));
    assertThat(res.getDetails(), equalTo("Bad request"));
  }

  @SneakyThrows
  @Test
  @DisplayName("DateFrom null")
  void dateFromNull() {
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));

    LocalDate dateTo = LocalDate.of(2023, 1, 30);
    url.append("?dateTo=").append(dateTo);

    ProblemJson res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(400)
                    .extract()
                    .as(ProblemJson.class);

    assertThat(res.getStatus(), equalTo(400));
    assertThat(res.getTitle(), equalTo("NODE_TECH_SUPPORT-0400"));
    assertThat(res.getDetails(), equalTo("Bad request"));
  }

  @SneakyThrows
  @Test
  @DisplayName("DateFrom after DateTo")
  void dateFromAfterDateTo() {
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));

    LocalDate dateTo = LocalDate.of(2023, 1, 30);
    LocalDate dateFrom = LocalDate.of(2023, 6, 30);
    url.append("?dateFrom=").append(dateFrom);
    url.append("&dateTo=").append(dateTo);

    ProblemJson res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(400)
                    .extract()
                    .as(ProblemJson.class);

    assertThat(res.getStatus(), equalTo(400));
    assertThat(res.getTitle(), equalTo("NODE_TECH_SUPPORT-0400"));
    assertThat(res.getDetails(), equalTo("Bad request"));
  }

  @SneakyThrows
  @Test
  @DisplayName("Wrong PA")
  void paWrong() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    String token = "pt_" + noticeNumber;
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted("PaWrong"));

    LocalDate dateFrom = LocalDate.of(2023, 1, 30);
    LocalDate dateTo = LocalDate.of(2023, 2, 6);

    url.append("?dateFrom=").append(dateFrom);
    url.append("&dateTo=").append(dateTo);

    PaymentResponse res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PaymentResponse>() {});

    assertThat(res.getCount(), equalTo(0L));
    assertThat(res.getMetadata().getTotPage(), equalTo(0));
    assertThat(res.getMetadata().getPageNumber(), equalTo(1));
    assertThat(res.getMetadata().getPageSize(), equalTo(1000));
    assertThat(res.getDateFrom(), equalTo(dateFrom));
    assertThat(res.getDateTo(), equalTo(dateTo));
  }

  @SneakyThrows
  @Test
  @DisplayName("snapshot test ok")
  void ok() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));

    LocalDate dateFrom = LocalDate.of(2023, 1, 30);
    LocalDate dateTo = LocalDate.of(2023, 2, 6);

    url.append("?dateFrom=").append(dateFrom);
    url.append("&dateTo=").append(dateTo);

    PaymentResponse res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PaymentResponse>() {});

    assertThat(res.getCount(), equalTo(10L));
    assertThat(res.getMetadata().getTotPage(), equalTo(1));
    assertThat(res.getMetadata().getPageNumber(), equalTo(1));
    assertThat(res.getMetadata().getPageSize(), equalTo(1000));
    assertThat(res.getDateFrom(), equalTo(dateFrom));
    assertThat(res.getDateTo(), equalTo(dateTo));
    assertThat(res.getData(), hasItem(anyOf(
            hasProperty("status", equalTo("CANCELLED")),
            hasProperty("insertedBy", equalTo("nodoInviaCarrelloRPT"))
    )));
  }

  @SneakyThrows
  @Test
  @DisplayName("No records found for future dates")
  void datesInFuture() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));

    LocalDate dateFrom = LocalDate.of(2024, 1, 30);
    LocalDate dateTo = LocalDate.of(2024, 2, 6);

    url.append("?dateFrom=").append(dateFrom);
    url.append("&dateTo=").append(dateTo);

    PaymentResponse res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PaymentResponse>() {});

    assertThat(res.getCount(), equalTo(0L));
    assertThat(res.getMetadata().getTotPage(), equalTo(0));
    assertThat(res.getMetadata().getPageNumber(), equalTo(1));
    assertThat(res.getMetadata().getPageSize(), equalTo(1000));
    assertThat(res.getDateFrom(), equalTo(dateFrom));
    assertThat(res.getDateTo(), equalTo(dateTo));
    assertThat(res.getData().size(), equalTo(0));
  }

  @SneakyThrows
  @Test
  @DisplayName("OK pagination 1 record per page")
  void oneResultPerPage() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    String token = "pt_" + noticeNumber;
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));

    LocalDate dateFrom = LocalDate.of(2023, 1, 30);
    LocalDate dateTo = LocalDate.of(2023, 2, 6);

    url.append("?dateFrom=").append(dateFrom);
    url.append("&dateTo=").append(dateTo);

    url.append("&size=");
    url.append("1");

    PaymentResponse res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PaymentResponse>() {});

    assertThat(res.getCount(), equalTo(10L));
    assertThat(res.getMetadata().getTotPage(), equalTo(10));
    assertThat(res.getMetadata().getPageNumber(), equalTo(1));
    assertThat(res.getMetadata().getPageSize(), equalTo(1));
    assertThat(res.getDateFrom(), equalTo(dateFrom));
    assertThat(res.getDateTo(), equalTo(dateTo));
    assertThat(res.getData().size(), equalTo(1));
  }

  @SneakyThrows
  @Test
  @DisplayName("Get third page and check paymentToken")
  void rightPaymentTokenInChosenPage() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));

    LocalDate dateFrom = LocalDate.of(2023, 1, 30);
    LocalDate dateTo = LocalDate.of(2023, 2, 6);

    url.append("?dateFrom=").append(dateFrom);
    url.append("&dateTo=").append(dateTo);

    url.append("&page=");
    url.append("3");

    url.append("&size=");
    url.append("1");

    PaymentResponse res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PaymentResponse>() {});

    assertThat(res.getCount(), equalTo(10L));
    assertThat(res.getMetadata().getTotPage(), equalTo(10));
    assertThat(res.getMetadata().getPageNumber(), equalTo(3));
    assertThat(res.getMetadata().getPageSize(), equalTo(1));
    assertThat(res.getDateFrom(), equalTo(dateFrom));
    assertThat(res.getDateTo(), equalTo(dateTo));
    assertThat(res.getData().size(), equalTo(1));
    assertThat(res.getData(), hasItem(anyOf(
            hasProperty("paymentToken", equalTo("9360bcf8d2ad410080241a623f05002d")),
            hasProperty("status", equalTo("CANCELLED_NORPT"))
    )));
  }

  @SneakyThrows
  @Test
  @DisplayName("Find by noticeNumber")
  void findByNoticeNumber() {
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));
    url.append("?noticeNumber=");
    url.append("312116313084213084");

    LocalDate dateFrom = LocalDate.of(2023, 1, 30);
    LocalDate dateTo = LocalDate.of(2023, 2, 6);

    url.append("&dateFrom=").append(dateFrom);
    url.append("&dateTo=").append(dateTo);

    PaymentResponse res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PaymentResponse>() {});

    assertThat(res.getCount(), equalTo(1L));
    assertThat(res.getMetadata().getTotPage(), equalTo(1));
    assertThat(res.getMetadata().getPageNumber(), equalTo(1));
    assertThat(res.getMetadata().getPageSize(), equalTo(1000));
    assertThat(res.getDateFrom(), equalTo(dateFrom));
    assertThat(res.getDateTo(), equalTo(dateTo));
    assertThat(res.getData().size(), equalTo(1));
    assertThat(res.getData(), hasItem(anyOf(
            hasProperty("noticeNumber", equalTo("312116313084213084"))
    )));
  }

  @SneakyThrows
  @Test
  @DisplayName("Find by paymentToken")
  void findByPaymentToken() {
    StringBuilder url = new StringBuilder(POS_PAY_SS_INFO_PATH.formatted(PA_CODE));
    url.append("?paymentToken=");
    url.append("e2d633ae0dea4264872d6920102f0f57");

    LocalDate dateFrom = LocalDate.of(2023, 1, 30);
    LocalDate dateTo = LocalDate.of(2023, 2, 6);

    url.append("&dateFrom=").append(dateFrom);
    url.append("&dateTo=").append(dateTo);

    PaymentResponse res =
            given()
                    .header(HEADER)
                    .when()
                    .get(url.toString())
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<PaymentResponse>() {});

    assertThat(res.getCount(), equalTo(1L));
    assertThat(res.getMetadata().getTotPage(), equalTo(1));
    assertThat(res.getMetadata().getPageNumber(), equalTo(1));
    assertThat(res.getMetadata().getPageSize(), equalTo(1000));
    assertThat(res.getDateFrom(), equalTo(dateFrom));
    assertThat(res.getDateTo(), equalTo(dateTo));
    assertThat(res.getData().size(), equalTo(1));
    assertThat(res.getData(), hasItem(anyOf(
            hasProperty("paymentToken", equalTo("e2d633ae0dea4264872d6920102f0f57"))
    )));
  }

}
