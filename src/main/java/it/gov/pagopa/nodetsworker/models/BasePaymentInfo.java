package it.gov.pagopa.nodetsworker.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BasePaymentInfo {

    private String organizationFiscalCode;
    private String noticeNumber;
    private String pspId;
    private String brokerPspId;
    private String channelId;
    private String outcome;
    private String status;
    private LocalDateTime insertedTimestamp;
    private LocalDateTime updatedTimestamp;
    private Boolean isOldPaymentModel;
    private String nodeId;
}
