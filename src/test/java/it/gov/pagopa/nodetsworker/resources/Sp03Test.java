package it.gov.pagopa.nodetsworker.resources;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import it.gov.pagopa.nodetsworker.repository.CosmosBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosNegBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosVerifyKOEventClient;
import it.gov.pagopa.nodetsworker.resources.response.TransactionResponse;
import it.gov.pagopa.nodetsworker.util.AppConstantTestHelper;
import it.gov.pagopa.nodetsworker.util.CosmosResource;
import it.gov.pagopa.nodetsworker.util.Util;
import lombok.SneakyThrows;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static it.gov.pagopa.nodetsworker.util.AppConstantTestHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

@QuarkusTest
//@QuarkusTestResource(AzuriteResource.class)
@QuarkusTestResource(CosmosResource.class)
class Sp03Test {

  @ConfigProperty(name = "biz.endpoint")
  String bizendpoint;

  @ConfigProperty(name = "biz.key")
  String bizkey;

  private CosmosClient clientbiz;

  private CosmosClient getCosmosClient() {
    if (clientbiz == null) {
      clientbiz = new CosmosClientBuilder().endpoint(bizendpoint).key(bizkey).buildClient();
      clientbiz.createDatabaseIfNotExists(CosmosBizEventClient.dbname);
      clientbiz.createDatabaseIfNotExists(CosmosNegBizEventClient.dbname);
      clientbiz.createDatabaseIfNotExists(CosmosVerifyKOEventClient.dbname);
      clientbiz
          .getDatabase(CosmosBizEventClient.dbname)
          .createContainerIfNotExists(CosmosBizEventClient.tablename, "/timestamp");
      clientbiz
          .getDatabase(CosmosNegBizEventClient.dbname)
          .createContainerIfNotExists(CosmosNegBizEventClient.tablename, "/timestamp");
      clientbiz
          .getDatabase(CosmosVerifyKOEventClient.dbname)
          .createContainerIfNotExists(CosmosVerifyKOEventClient.tablename, "/timestamp");
    }
    return clientbiz;
  }

  @SneakyThrows
  @Test
  @DisplayName("sp03 by ci and nn with positive")
  void test1() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_NN.formatted(PA_CODE, noticeNumber);

    getCosmosClient()
        .getDatabase(CosmosBizEventClient.dbname)
        .getContainer(CosmosBizEventClient.tablename)
        .createItem(
            AppConstantTestHelper.newPositiveBiz(PA_CODE, noticeNumber, null),
            new CosmosItemRequestOptions());

    TransactionResponse res =
        given()
            .param("dateFrom", Util.format(LocalDate.now().minusDays(2)))
            .param("dateTo", Util.format(LocalDate.now().plusDays(2)))
            .when()
            .get(url)
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<TransactionResponse<PaymentInfo>>() {});
    assertThat(res.getPayments().size(), greaterThan(0));
    PaymentInfo o = (PaymentInfo) res.getPayments().get(0);
    assertThat(o.getNoticeNumber(), equalTo(noticeNumber));
    assertThat(o.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o.getOutcome(), equalTo(AppConstantTestHelper.outcomeOK));
    assertThat(o.getPspId(), equalTo("pspTest"));
    assertThat(o.getChannelId(), equalTo("canaleTest"));
    assertThat(o.getBrokerPspId(), equalTo("intTest"));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp03 by ci and nn with positive and verifyKO")
  void test1_2() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_NN.formatted(PA_CODE, noticeNumber);

    getCosmosClient()
            .getDatabase(CosmosVerifyKOEventClient.dbname)
            .getContainer(CosmosVerifyKOEventClient.tablename)
            .createItem(
                    AppConstantTestHelper.newVerifyKO(PA_CODE, noticeNumber),
                    new CosmosItemRequestOptions());
    getCosmosClient()
            .getDatabase(CosmosBizEventClient.dbname)
            .getContainer(CosmosBizEventClient.tablename)
            .createItem(
                    AppConstantTestHelper.newPositiveBiz(PA_CODE, noticeNumber, null),
                    new CosmosItemRequestOptions());

    TransactionResponse res =
            given()
                    .param("dateFrom", Util.format(LocalDate.now().minusDays(2)))
                    .param("dateTo", Util.format(LocalDate.now().plusDays(2)))
                    .when()
                    .get(url)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<TransactionResponse<PaymentInfo>>() {});
    assertThat(res.getPayments().size(), equalTo(2));
    PaymentInfo o = (PaymentInfo) res.getPayments().get(0);
    assertThat(o.getNoticeNumber(), equalTo(noticeNumber));
    assertThat(o.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o.getPspId(), equalTo("pspTest"));
    assertThat(o.getChannelId(), equalTo("canaleTest"));
    assertThat(o.getBrokerPspId(), equalTo("intTest"));
    assertThat(o.getFaultBean().getFaultCode(), equalTo("FAULT_CODE"));
    PaymentInfo o2 = (PaymentInfo) res.getPayments().get(1);
    assertThat(o2.getNoticeNumber(), equalTo(noticeNumber));
    assertThat(o2.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o2.getPspId(), equalTo("pspTest"));
    assertThat(o2.getChannelId(), equalTo("canaleTest"));
    assertThat(o2.getBrokerPspId(), equalTo("intTest"));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp03 by ci and nn with negative")
  void test2() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_NN.formatted(PA_CODE, noticeNumber);

    getCosmosClient()
        .getDatabase(CosmosBizEventClient.dbname)
        .getContainer(CosmosNegBizEventClient.tablename)
        .createItem(
            AppConstantTestHelper.newNegBiz(PA_CODE, noticeNumber, null, false),
            new CosmosItemRequestOptions());

    TransactionResponse res =
        given()
            .param("dateFrom", Util.format(LocalDate.now()))
            .param("dateTo", Util.format(LocalDate.now()))
            .when()
            .get(url)
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<TransactionResponse<PaymentInfo>>() {});
    assertThat(res.getPayments().size(), greaterThan(0));
    PaymentInfo o = (PaymentInfo) res.getPayments().get(0);
    assertThat(o.getNoticeNumber(), equalTo(noticeNumber));
    assertThat(o.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o.getPspId(), equalTo("pspTest"));
    assertThat(o.getChannelId(), equalTo("canaleTest"));
    assertThat(o.getBrokerPspId(), equalTo("intTest"));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp03 by ci and iuv with positive")
  void test3() {
    String iuv = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_IUV.formatted(PA_CODE, iuv);

    getCosmosClient()
        .getDatabase(CosmosBizEventClient.dbname)
        .getContainer(CosmosBizEventClient.tablename)
        .createItem(
            AppConstantTestHelper.newPositiveBiz(PA_CODE, null, iuv),
            new CosmosItemRequestOptions());

    TransactionResponse res =
        given()
            .param("dateFrom", Util.format(LocalDate.now()))
            .param("dateTo", Util.format(LocalDate.now()))
            .when()
            .get(url)
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<TransactionResponse<PaymentInfo>>() {});
    assertThat(res.getPayments().size(), greaterThan(0));
    PaymentInfo o = (PaymentInfo) res.getPayments().get(0);
    assertThat(o.getIuv(), equalTo(iuv));
    assertThat(o.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o.getOutcome(), equalTo(AppConstantTestHelper.outcomeOK));
    assertThat(o.getPspId(), equalTo("pspTest"));
    assertThat(o.getChannelId(), equalTo("canaleTest"));
    assertThat(o.getBrokerPspId(), equalTo("intTest"));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp03 by ci and iuv with negative")
  void test4() {
    String iuv = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_IUV.formatted(PA_CODE, iuv);

    getCosmosClient()
        .getDatabase(CosmosBizEventClient.dbname)
        .getContainer(CosmosNegBizEventClient.tablename)
        .createItem(
            AppConstantTestHelper.newNegBiz(PA_CODE, null, iuv, false),
            new CosmosItemRequestOptions());

    TransactionResponse res =
        given()
            .param("dateFrom", Util.format(LocalDate.now()))
            .param("dateTo", Util.format(LocalDate.now()))
            .when()
            .get(url)
            .then()
            .statusCode(200)
            .extract()
            .as(new TypeRef<TransactionResponse<PaymentInfo>>() {});
    assertThat(res.getPayments().size(), greaterThan(0));
    PaymentInfo o = (PaymentInfo) res.getPayments().get(0);
    assertThat(o.getIuv(), equalTo(iuv));
    assertThat(o.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o.getPspId(), equalTo("pspTest"));
    assertThat(o.getChannelId(), equalTo("canaleTest"));
    assertThat(o.getBrokerPspId(), equalTo("intTest"));
  }

  @SneakyThrows
  @Test
  @DisplayName("dateFrom 400")
  void test5() {
    String iuv = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_IUV.formatted(PA_CODE, iuv);

    given().param("dateFrom", Util.format(LocalDate.now())).when().get(url).then().statusCode(400);
  }

  @SneakyThrows
  @Test
  @DisplayName("dateTo 400")
  void test6() {
    String iuv = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_IUV.formatted(PA_CODE, iuv);

    given().param("dateTo", Util.format(LocalDate.now())).when().get(url).then().statusCode(400);
  }

  @SneakyThrows
  @Test
  @DisplayName("bad date 400")
  void test7() {
    String iuv = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_IUV.formatted(PA_CODE, iuv);

    RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());

    given().param("dateTo", "aaa").when().get(url).then().statusCode(404);
  }
}
