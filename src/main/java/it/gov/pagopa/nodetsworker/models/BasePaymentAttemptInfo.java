package it.gov.pagopa.nodetsworker.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BasePaymentAttemptInfo {

    private String organizationFiscalCode;
    private String noticeNumber;
    private String pspId;
    private String brokerPspId;
    private String channelId;
    private String brokerOrganizationId;
    private String stationId;
    private String paymentMethod;
    private Boolean flagIO;
    private String outcome;
    private String status;
    private LocalDateTime insertedTimestamp;
    private LocalDateTime updatedTimestamp;

    private String nodeId;
}
