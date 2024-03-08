package it.gov.pagopa.nodetsworker.resources;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import it.gov.pagopa.nodetsworker.models.PaymentAttemptInfo;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import it.gov.pagopa.nodetsworker.repository.CosmosBizEventRepository;
import it.gov.pagopa.nodetsworker.repository.CosmosNegBizEventClient;
import it.gov.pagopa.nodetsworker.repository.CosmosVerifyKOEventClient;
import it.gov.pagopa.nodetsworker.repository.model.*;
import it.gov.pagopa.nodetsworker.resources.response.TransactionResponse;
import it.gov.pagopa.nodetsworker.util.AppConstantTestHelper;
import it.gov.pagopa.nodetsworker.util.Util;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import lombok.SneakyThrows;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static it.gov.pagopa.nodetsworker.util.AppConstantTestHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class Sp03Test {

  private Psp psp = new Psp(INT_PSP_CODE,CHANNEL_CODE,PSP_CODE,"","","","");
  private DebtorPosition dp = new DebtorPosition("","","");
  private it.gov.pagopa.nodetsworker.repository.model.PaymentInfo pi = new it.gov.pagopa.nodetsworker.repository.model.PaymentInfo("","","","","","", BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE,"","",1l,"","","",null,"");
  private it.gov.pagopa.nodetsworker.repository.model.NegativePaymentInfo npi = new it.gov.pagopa.nodetsworker.repository.model.NegativePaymentInfo("",LocalDate.now(),"", BigDecimal.ONE,10l,"","","",null);
  private Fault fb = new Fault("FAULT_CODE","",10000l,"");
  private Creditor creditor = new Creditor(PA_CODE,"","","","");

  private static Stream streamVerify = Mockito.mock(Stream.class);
  private static Stream streamBiz = Mockito.mock(Stream.class);
  private static Stream streamBizneg = Mockito.mock(Stream.class);

    @Produces
    @Named("verifyKo")
    @ApplicationScoped
    CosmosClient verifyKo() {
      CosmosPagedIterable cosmosPagedIterable = Mockito.mock(CosmosPagedIterable.class);
      CosmosDatabase cosmosDatabase = Mockito.mock(CosmosDatabase.class);
      CosmosContainer cosmosContainer = Mockito.mock(CosmosContainer.class);
      CosmosClient cosmosClient = Mockito.mock(CosmosClient.class);
      when(cosmosPagedIterable.stream()).thenReturn(streamVerify);
      when(cosmosContainer.queryItems(any(SqlQuerySpec.class),any(),any())).thenReturn(cosmosPagedIterable);
      when(cosmosDatabase.getContainer(CosmosVerifyKOEventClient.tablename)).thenReturn(cosmosContainer);
      when(cosmosClient.getDatabase(CosmosVerifyKOEventClient.dbname)).thenReturn(cosmosDatabase);
      return cosmosClient;
    }

  @Produces
    @Named("bizneg")
    @ApplicationScoped
    CosmosClient negbizClient() {
      CosmosPagedIterable cosmosPagedIterable = Mockito.mock(CosmosPagedIterable.class);
      CosmosDatabase cosmosDatabase = Mockito.mock(CosmosDatabase.class);
      CosmosContainer cosmosContainer = Mockito.mock(CosmosContainer.class);
      CosmosClient cosmosClient = Mockito.mock(CosmosClient.class);
      when(cosmosPagedIterable.stream()).thenReturn(streamBizneg);
      when(cosmosContainer.queryItems(any(SqlQuerySpec.class),any(),any())).thenReturn(cosmosPagedIterable);
      when(cosmosDatabase.getContainer(CosmosNegBizEventClient.tablename)).thenReturn(cosmosContainer);
      when(cosmosClient.getDatabase(CosmosNegBizEventClient.dbname)).thenReturn(cosmosDatabase);
      return cosmosClient;
    }

    @Produces
    @Named("biz")
    @ApplicationScoped
    CosmosClient biz() {
      CosmosPagedIterable cosmosPagedIterable = Mockito.mock(CosmosPagedIterable.class);
      CosmosDatabase cosmosDatabase = Mockito.mock(CosmosDatabase.class);
      CosmosContainer cosmosContainer = Mockito.mock(CosmosContainer.class);
      CosmosClient cosmosClient = Mockito.mock(CosmosClient.class);
      when(cosmosPagedIterable.stream()).thenReturn(streamBiz);
      when(cosmosContainer.queryItems(any(SqlQuerySpec.class),any(),any())).thenReturn(cosmosPagedIterable);
      when(cosmosDatabase.getContainer(CosmosBizEventRepository.tablename)).thenReturn(cosmosContainer);
      when(cosmosClient.getDatabase(CosmosBizEventRepository.dbname)).thenReturn(cosmosDatabase);
      return cosmosClient;
    }

  @SneakyThrows
  @Test
  @DisplayName("sp03 by ci and nn with positive")
  void test1() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_NN.formatted(PA_CODE, noticeNumber);

    dp.setNoticeNumber(noticeNumber);

    when(streamBiz.toList()).thenReturn(Arrays.asList(
            new PositiveBizEvent("", "", "","",dp,creditor,psp,null,null,pi,null,null,10l,null)
    ));
    when(streamVerify.toList()).thenReturn(Arrays.asList(
            new VerifyKOEvent("", "", dp,creditor,psp,fb,"","","")
    ));
    when(streamBizneg.toList()).thenReturn(Arrays.asList(
            new NegativeBizEvent("", "", "","",true,dp,creditor,psp,null,npi,null,null,10l,null)
    ));

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
    assertThat(o.getOutcome(), equalTo(outcomeKO));
    assertThat(o.getPspId(), equalTo(PSP_CODE));
    assertThat(o.getChannelId(), equalTo(CHANNEL_CODE));
    assertThat(o.getBrokerPspId(), equalTo(INT_PSP_CODE));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp03 by ci and nn with positive and verifyKO")
  void test1_2() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_NN.formatted(PA_CODE, noticeNumber);

    dp.setNoticeNumber(noticeNumber);

    when(streamVerify.toList()).thenReturn(Arrays.asList(
            new VerifyKOEvent("", "", dp,creditor,psp,fb,"","","")
    ));
    when(streamBiz.toList()).thenReturn(Arrays.asList(
            new PositiveBizEvent("", "", "","",dp,creditor,psp,null,null,pi,null,null,10l,null)
    ));
    when(streamBizneg.toList()).thenReturn(Arrays.asList());

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
    assertThat(o.getPspId(), equalTo(PSP_CODE));
    assertThat(o.getChannelId(), equalTo(CHANNEL_CODE));
    assertThat(o.getBrokerPspId(), equalTo(INT_PSP_CODE));
    assertThat(o.getFaultBean().getFaultCode(), equalTo("FAULT_CODE"));
    PaymentInfo o2 = (PaymentInfo) res.getPayments().get(1);
    assertThat(o2.getNoticeNumber(), equalTo(noticeNumber));
    assertThat(o2.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o2.getPspId(), equalTo(PSP_CODE));
    assertThat(o2.getChannelId(), equalTo(CHANNEL_CODE));
    assertThat(o2.getBrokerPspId(), equalTo(INT_PSP_CODE));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp03 by ci and nn with negative")
  void test2() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_NN.formatted(PA_CODE, noticeNumber);

    dp.setNoticeNumber(noticeNumber);

    when(streamBiz.toList()).thenReturn(Arrays.asList(
            new PositiveBizEvent("", "", "","",dp,creditor,psp,null,null,pi,null,null,10l,null)
    ));
    when(streamVerify.toList()).thenReturn(Arrays.asList(
            new VerifyKOEvent("", "", dp,creditor,psp,fb,"","","")
    ));
    when(streamBizneg.toList()).thenReturn(Arrays.asList(
            new NegativeBizEvent("", "", "","",true,dp,creditor,psp,null,npi,null,null,10l,null)
    ));

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
    assertThat(o.getPspId(), equalTo(PSP_CODE));
    assertThat(o.getChannelId(), equalTo(CHANNEL_CODE));
    assertThat(o.getBrokerPspId(), equalTo(INT_PSP_CODE));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp03 by ci and iuv with positive")
  void test3() {
    String iuv = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_IUV.formatted(PA_CODE, iuv);

    dp.setIuv(iuv);

    when(streamBiz.toList()).thenReturn(Arrays.asList(
            new PositiveBizEvent("", "", "","",dp,creditor,psp,null,null,pi,null,null,10l,null)
    ));
    when(streamVerify.toList()).thenReturn(Arrays.asList(
    ));
    when(streamBizneg.toList()).thenReturn(Arrays.asList(
    ));

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
    assertThat(o.getPspId(), equalTo(PSP_CODE));
    assertThat(o.getChannelId(), equalTo(CHANNEL_CODE));
    assertThat(o.getBrokerPspId(), equalTo(INT_PSP_CODE));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp03 by ci and iuv with negative")
  void test4() {
    String iuv = String.valueOf(Instant.now().toEpochMilli());
    String url = SP03_IUV.formatted(PA_CODE, iuv);

    dp.setIuv(iuv);

    when(streamBiz.toList()).thenReturn(Arrays.asList(
    ));
    when(streamVerify.toList()).thenReturn(Arrays.asList(
    ));
    when(streamBizneg.toList()).thenReturn(Arrays.asList(
            new NegativeBizEvent("", "", "","",true,dp,creditor,psp,null,npi,null,null,10l,null)
    ));


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
    assertThat(o.getPspId(), equalTo(PSP_CODE));
    assertThat(o.getChannelId(), equalTo(CHANNEL_CODE));
    assertThat(o.getBrokerPspId(), equalTo(INT_PSP_CODE));
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


  @SneakyThrows
  @Test
  @DisplayName("sp04 by ci,nn,token with positive")
  void sp04_test1() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    String token = "pt_" + noticeNumber;
    String url = SP04_NN.formatted(PA_CODE, noticeNumber, token);

    pi.setPaymentToken(token);
    dp.setNoticeNumber(noticeNumber);

    when(streamBiz.toList()).thenReturn(Arrays.asList(
            new PositiveBizEvent("", "", "","",dp,creditor,psp,null,null,pi,null,null,10l,null)
    ));
    when(streamVerify.toList()).thenReturn(Arrays.asList(
    ));
    when(streamBizneg.toList()).thenReturn(Arrays.asList(
    ));

    TransactionResponse res =
            given()
                    .param("dateFrom", Util.format(LocalDate.now()))
                    .param("dateTo", Util.format(LocalDate.now()))
                    .when()
                    .get(url)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<TransactionResponse<PaymentAttemptInfo>>() {});
    assertThat(res.getPayments().size(), greaterThan(0));
    PaymentAttemptInfo o = (PaymentAttemptInfo) res.getPayments().get(0);
    assertThat(o.getNoticeNumber(), equalTo(noticeNumber));
    assertThat(o.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o.getPspId(), equalTo(PSP_CODE));
    assertThat(o.getChannelId(), equalTo(CHANNEL_CODE));
    assertThat(o.getBrokerPspId(), equalTo(INT_PSP_CODE));
    assertThat(o.getPaymentToken(), equalTo(token));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp04 by ci,nn,token with negative")
  void sp04_test2() {
    String noticeNumber = String.valueOf(Instant.now().toEpochMilli());
    String token = "pt_" + noticeNumber;
    String url = SP04_NN.formatted(PA_CODE, noticeNumber, token);

    npi.setPaymentToken(token);
    dp.setNoticeNumber(noticeNumber);

    when(streamBiz.toList()).thenReturn(Arrays.asList(
    ));
    when(streamVerify.toList()).thenReturn(Arrays.asList(
    ));
    when(streamBizneg.toList()).thenReturn(Arrays.asList(
            new NegativeBizEvent("", "", "","",true,dp,creditor,psp,null,npi,null,null,10l,null)
    ));

    TransactionResponse res =
            given()
                    .param("dateFrom", Util.format(LocalDate.now()))
                    .param("dateTo", Util.format(LocalDate.now()))
                    .when()
                    .get(url)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<TransactionResponse<PaymentAttemptInfo>>() {});
    assertThat(res.getPayments().size(), greaterThan(0));
    PaymentAttemptInfo o = (PaymentAttemptInfo) res.getPayments().get(0);
    assertThat(o.getNoticeNumber(), equalTo(noticeNumber));
    assertThat(o.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o.getPspId(), equalTo(PSP_CODE));
    assertThat(o.getChannelId(), equalTo(CHANNEL_CODE));
    assertThat(o.getBrokerPspId(), equalTo(INT_PSP_CODE));
    assertThat(o.getPaymentToken(), equalTo(token));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp04 by ci,iuv,ccp with positive")
  void sp04_test3() {
    String iuv = String.valueOf(Instant.now().toEpochMilli());
    String ccp = "ccp_" + iuv;
    String url = SP04_IUV.formatted(PA_CODE, iuv, ccp);

    pi.setPaymentToken(ccp);
    dp.setIuv(iuv);

    when(streamBiz.toList()).thenReturn(Arrays.asList(
            new PositiveBizEvent("", "", "","",dp,creditor,psp,null,null,pi,null,null,10l,null)
    ));
    when(streamVerify.toList()).thenReturn(Arrays.asList(
    ));
    when(streamBizneg.toList()).thenReturn(Arrays.asList(
    ));

    TransactionResponse res =
            given()
                    .param("dateFrom", Util.format(LocalDate.now()))
                    .param("dateTo", Util.format(LocalDate.now()))
                    .when()
                    .get(url)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<TransactionResponse<PaymentAttemptInfo>>() {});
    assertThat(res.getPayments().size(), greaterThan(0));
    PaymentAttemptInfo o = (PaymentAttemptInfo) res.getPayments().get(0);
    assertThat(o.getIuv(), equalTo(iuv));
    assertThat(o.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o.getPspId(), equalTo(PSP_CODE));
    assertThat(o.getChannelId(), equalTo(CHANNEL_CODE));
    assertThat(o.getBrokerPspId(), equalTo(INT_PSP_CODE));
    assertThat(o.getPaymentToken(), equalTo(ccp));
  }

  @SneakyThrows
  @Test
  @DisplayName("sp04 by ci,iuv,ccp with negative")
  void sp04_test4() {
    String iuv = String.valueOf(Instant.now().toEpochMilli());
    String ccp = "ccp_" + iuv;
    String url = SP04_IUV.formatted(PA_CODE, iuv, ccp);

    npi.setPaymentToken(ccp);
    dp.setIuv(iuv);

    when(streamBiz.toList()).thenReturn(Arrays.asList(
    ));
    when(streamVerify.toList()).thenReturn(Arrays.asList(
    ));
    when(streamBizneg.toList()).thenReturn(Arrays.asList(
            new NegativeBizEvent("", "", "","",true,dp,creditor,psp,null,npi,null,null,10l,null)
    ));

    TransactionResponse res =
            given()
                    .param("dateFrom", Util.format(LocalDate.now()))
                    .param("dateTo", Util.format(LocalDate.now()))
                    .when()
                    .get(url)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(new TypeRef<TransactionResponse<PaymentAttemptInfo>>() {});
    assertThat(res.getPayments().size(), greaterThan(0));
    PaymentAttemptInfo o = (PaymentAttemptInfo) res.getPayments().get(0);
    assertThat(o.getIuv(), equalTo(iuv));
    assertThat(o.getOrganizationFiscalCode(), equalTo(PA_CODE));
    assertThat(o.getPspId(), equalTo(PSP_CODE));
    assertThat(o.getChannelId(), equalTo(CHANNEL_CODE));
    assertThat(o.getBrokerPspId(), equalTo(INT_PSP_CODE));
    assertThat(o.getPaymentToken(), equalTo(ccp));
  }

  @SneakyThrows
  @Test
  @DisplayName("dateFrom 400")
  void sp04_test5() {
    String iuv = String.valueOf(new Random().nextLong(11111111111l, 99999999999l));
    String ccp = "ccp_" + iuv;
    String url = SP04_IUV.formatted(PA_CODE, iuv, ccp);

    given().param("dateFrom", Util.format(LocalDate.now())).when().get(url).then().statusCode(400);
  }

  @SneakyThrows
  @Test
  @DisplayName("dateTo 400")
  void sp04_test6() {
    String iuv = String.valueOf(new Random().nextLong(11111111111l, 99999999999l));
    String ccp = "ccp_" + iuv;
    String url = SP04_IUV.formatted(PA_CODE, iuv, ccp);

    given().param("dateTo", Util.format(LocalDate.now())).when().get(url).then().statusCode(400);
  }
}
