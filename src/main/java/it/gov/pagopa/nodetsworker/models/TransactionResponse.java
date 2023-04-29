package it.gov.pagopa.nodetsworker.models;

import it.gov.pagopa.nodetsworker.mappers.YesNoConverter;
import lombok.*;

import javax.persistence.Column;
import javax.persistence.Convert;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    private String organizationFiscalCode;
    private String organizationName;
    private String noticeNumber;
    private String creditorReferenceId;
    private String paymentToken;
    private String pspId;
    private String brokerPspId;
    private String channelId;
    private String outcome;
    private LocalDate transferDate;
    private Long payerId;
    private Boolean isOldModelPayment;
    private String ccp;
    private String bic;
    private LocalDateTime paymentRequestTimestamp;
    private Boolean revokeRequest;
    private LocalDate applicationDate;
    private LocalDateTime insertedTimestamp;
    private LocalDateTime updatedTimestamp;

    private String nodeId;
}
