package it.gov.pagopa.nodetsworker.resources.response;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionPaymentStatusSnapshotInfo {

    private String paFiscalCode;
    private String noticeId;
    private String creditorReferenceId;
    private String paymentToken;
    private String status;
    private Instant insertedTimestamp;
    private Instant updatedTimestamp;
    private String insertedBy;
    private String updatedBy;

}
