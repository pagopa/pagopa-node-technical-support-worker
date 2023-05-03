package it.gov.pagopa.nodetsworker.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInfo {

    private String organizationFiscalCode;
    private String noticeNumber;
    private String paymentToken; // payment token if new payment model or ccp for old payment model
    private String pspId;
    private String brokerPspId;
    private String channelId;
    private String outcome;
    private String status;
    private LocalDateTime insertedTimestamp;
    private LocalDateTime updatedTimestamp;
    private Boolean isOldModelPayment;

    private String nodeId;
}
