package it.gov.pagopa.nodetsworker.util;

import com.azure.data.tables.models.TableEntity;
import io.restassured.http.Header;
import it.gov.pagopa.nodetsworker.repository.model.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.UUID;

public class AppConstantTestHelper {

  public static final String SP03_NN  = "/organizations/%s/noticeNumber/%s";
  public static final String SP03_IUV = "/organizations/%s/iuv/%s";
  public static final String SP04_NN  = "/organizations/%s/iuv/%s/paymentToken/%s";
  public static final String SP04_IUV = "/organizations/%s/iuv/%s/ccp/%s";

  public static final String PA_CODE = "12345678900";
  public static final String outcomeOK = "OK";

  public static final Header HEADER = new Header("Content-Type", "application/json");

  public static final TableEntity newRe(String pa,String noticeNumber){
    TableEntity entity = new TableEntity(LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_DATE_TIME), String.valueOf(noticeNumber));
    entity.addProperty("idDominio",pa);
    entity.addProperty("noticeNumber",noticeNumber);
    entity.addProperty("esito","CAMBIO_STATO");
    entity.addProperty("paymentToken","pt_"+noticeNumber);
    return entity;
  }

  public static final PositiveBizEvent newBiz(String pa, String noticeNumber){
    PositiveBizEvent p = PositiveBizEvent.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .psp(
                Psp.builder()
                        .psp("pspTest")
                        .idChannel("canaleTest")
                        .build()
            )
            .creditor(
                    Creditor.builder().idPA(pa).build()
            ).debtorPosition(
                    DebtorPosition.builder().noticeNumber(noticeNumber).build()
            ).paymentInfo(
                    PaymentInfo.builder().paymentDateTime(LocalDateTime.now().minusDays(1)).build()
            )
            .build();
    return p;
  }
  public static final NegativeBizEvent newNegBiz(String pa, String noticeNumber){
    NegativeBizEvent p = NegativeBizEvent.builder()
            .id(UUID.randomUUID().toString())
            .creditor(
                    Creditor.builder().idPA(pa).build()
            ).debtorPosition(
                    DebtorPosition.builder().noticeNumber(noticeNumber).build()
            ).paymentInfo(
                    NegativePaymentInfo.builder()
                        .paymentDateTime(LocalDateTime.now().minusDays(1))
                        .build()
            )
            .build();
    return p;
  }
}
