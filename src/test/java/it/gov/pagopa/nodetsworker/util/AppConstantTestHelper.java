package it.gov.pagopa.nodetsworker.util;

import com.azure.data.tables.models.TableEntity;
import io.restassured.http.Header;
import it.gov.pagopa.nodetsworker.repository.model.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public class AppConstantTestHelper {

  public static final String SP03_NN = "/organizations/%s/noticeNumber/%s";
  public static final String SP03_IUV = "/organizations/%s/iuv/%s";

  public static final String SP04_NN = "/organizations/%s/iuv/%s/paymentToken/%s";
  public static final String SP04_IUV = "/organizations/%s/iuv/%s/ccp/%s";

  public static final String PA_CODE = "12345678900";
  public static final String outcomeOK = "OK";
  public static final String outcomeKO = "KO";

  public static final Header HEADER = new Header("Content-Type", "application/json");

  public static final TableEntity newRe(String pa, String noticeNumber, String iuv) {
    TableEntity entity =
        new TableEntity(
            Util.format(LocalDate.now()),
            String.valueOf(Optional.ofNullable(noticeNumber).orElse(iuv)));
    entity.addProperty("idDominio", pa);
    entity.addProperty("noticeNumber", noticeNumber);
    entity.addProperty("iuv", iuv);
    entity.addProperty("esito", "CAMBIO_STATO");
    if(noticeNumber!=null)
      entity.addProperty("paymentToken", "pt_" + noticeNumber);
    if(iuv!=null)
      entity.addProperty("ccp", "ccp_" + iuv);
    entity.addProperty("stazione", "77777777777_01");
    entity.addProperty("psp", "pspTest");
    entity.addProperty("canale", "canaleTest");
    entity.addProperty("status", "PAID");
    return entity;
  }

  public static final PositiveBizEvent newPositiveBiz(String pa, String noticeNumber, String iuv) {
    PositiveBizEvent p =
        PositiveBizEvent.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(Util.toMillis(LocalDateTime.now()))
            .psp(Psp.builder().psp("pspTest").idChannel("canaleTest").build())
            .creditor(Creditor.builder().idPA(pa).build())
            .debtorPosition(
                DebtorPosition.builder().modelType("1").iuv(iuv).noticeNumber(noticeNumber).build())
            .paymentInfo(
                PaymentInfo.builder()
                    .paymentToken(noticeNumber != null ? "pt_" + noticeNumber : "ccp_" + iuv)
                    .paymentDateTime(LocalDateTime.now())
                    .build())
            .build();
    return p;
  }

  public static final NegativeBizEvent newNegBiz(
      String pa, String noticeNumber, String iuv, boolean reawakable) {
    NegativeBizEvent p =
        NegativeBizEvent.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(Util.toMillis(LocalDateTime.now()))
            .creditor(Creditor.builder().idPA(pa).build())
            .debtorPosition(
                DebtorPosition.builder().iuv(iuv).noticeNumber(noticeNumber).modelType("1").build())
            .paymentInfo(
                NegativePaymentInfo.builder()
                    .paymentToken(noticeNumber != null ? "pt_" + noticeNumber : "ccp_" + iuv)
                    .paymentDateTime(LocalDateTime.now())
                    .build())
            .reAwakable(reawakable)
            .build();
    return p;
  }
}
