package it.gov.pagopa.nodetsworker.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PaymentInfo {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String businessProcess;
//  private String paymentStatus;

  private String organizationFiscalCode;
  private String noticeNumber;
  private String iuv;
  private String pspId;
  private String brokerPspId;
  private String channelId;
  private String outcome;
  private String status;
  private String insertedTimestamp;
//  private String updatedTimestamp;
  private String serviceIdentifier;

  private String paymentToken;
  private String ccp;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String positiveBizEvtId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String verifyKoEvtId;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String negativeBizEvtId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private FaultBean faultBean;
}
