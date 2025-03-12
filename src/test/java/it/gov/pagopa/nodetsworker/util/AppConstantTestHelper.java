package it.gov.pagopa.nodetsworker.util;

import com.azure.data.tables.models.TableEntity;
import io.restassured.http.Header;
import it.gov.pagopa.nodetsworker.repository.models.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

public class AppConstantTestHelper {

  public static final String SP03_NN = "/organizations/%s/noticeNumber/%s";
  public static final String SP03_IUV = "/organizations/%s/iuv/%s";

  public static final String SP04_NN = "/organizations/%s/noticeNumber/%s/paymentToken/%s";
  public static final String SP04_IUV = "/organizations/%s/iuv/%s/ccp/%s";

  public static final String POS_PAY_SS_INFO_PATH = "snapshot/organizations/%s";

  public static final String NEG_BIZ_EVENT_ID_PATH = "/events/negative/%s";

  public static final String PA_CODE = "12345678900";
  public static final String PSP_CODE = "P_12345678900";
  public static final String CHANNEL_CODE = "C_12345678900";
  public static final String INT_PSP_CODE = "I_12345678900";
  public static final String outcomeOK = "OK";
  public static final String outcomeKO = "KO";

  public static Long toMillis(LocalDateTime d) {
    return d.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  }

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

  public static final VerifyKOEvent newVerifyKO(String pa, String noticeNumber) {
    VerifyKOEvent p =
            VerifyKOEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .faultBean(
                            Fault.builder()
                                    .dateTime(LocalDateTime.now().toString())
                                    .timestamp(toMillis(LocalDateTime.now()))
                                    .build()
                    )
                    .psp(Psp.builder().idBrokerPsp("intTest").idPsp("pspTest").idChannel("canaleTest").build())
                    .creditor(Creditor.builder().idPA(pa).build())
                    .debtorPosition(
                            DebtorPosition.builder().modelType("1").noticeNumber(noticeNumber).build())
                    .faultBean(Fault.builder().faultCode("FAULT_CODE").description("DESCRIPTION").dateTime(LocalDateTime.now().toString()).build())
                    .build();
    return p;
  }

  public static final PositiveBizEvent newPositiveBiz(String pa, String noticeNumber, String iuv) {
    PositiveBizEvent p =
        PositiveBizEvent.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(toMillis(LocalDateTime.now()))
            .psp(Psp.builder().idBrokerPsp("intTest").idPsp("pspTest").idChannel("canaleTest").build())
            .creditor(Creditor.builder().idPA(pa).build())
            .debtorPosition(
                DebtorPosition.builder().modelType("1").iuv(iuv).noticeNumber(noticeNumber).build())
            .paymentInfo(
                PaymentInfo.builder()
                    .paymentToken(noticeNumber != null ? "pt_" + noticeNumber : "ccp_" + iuv)
                    .paymentDateTime(LocalDateTime.now().toString())
                    .build())
            .build();
    return p;
  }

  public static final NegativeBizEvent newNegBiz(
      String pa, String noticeNumber, String iuv, boolean reawakable) {
    NegativeBizEvent p =
        NegativeBizEvent.builder()
            .id(UUID.randomUUID().toString())
            .timestamp(toMillis(LocalDateTime.now()))
                .psp(Psp.builder().idBrokerPsp("intTest").idPsp("pspTest").idChannel("canaleTest").build())

                .creditor(Creditor.builder().idPA(pa).build())
            .debtorPosition(
                DebtorPosition.builder().iuv(iuv).noticeNumber(noticeNumber).modelType("1").build())
            .paymentInfo(
                NegativePaymentInfo.builder()
                    .paymentToken(noticeNumber != null ? "pt_" + noticeNumber : "ccp_" + iuv)
                    .paymentDateTime(LocalDateTime.now().toString())
                    .build())
            .reAwakable(reawakable)
            .build();
    return p;
  }
}
