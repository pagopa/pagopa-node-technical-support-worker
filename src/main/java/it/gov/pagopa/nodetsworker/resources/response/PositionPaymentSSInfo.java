package it.gov.pagopa.nodetsworker.resources.response;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionPaymentSSInfo {

    private String organizationFiscalCode;
    private String noticeNumber;
    private String creditorReferenceId;
    private String paymentToken;
    private String status;
    private Instant insertedTimestamp;
    private Instant updatedTimestamp;
    private String insertedBy;
    private String updatedBy;
    private String serviceIdentifier;

}
