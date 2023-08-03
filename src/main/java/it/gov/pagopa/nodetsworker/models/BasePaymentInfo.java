package it.gov.pagopa.nodetsworker.models;

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
public class BasePaymentInfo {

  private String organizationFiscalCode;
  private String noticeNumber;
  private String iuv;
  private String pspId;
  private String brokerPspId;
  private String channelId;
  private String outcome;
  private String status;
  private String insertedTimestamp;
  private String updatedTimestamp;
  private String serviceIdentifier;

  private String positiveBizEvtId;
  private String negativeBizEvtId;
}
