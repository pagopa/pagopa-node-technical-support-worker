package it.gov.pagopa.nodetsworker.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PositionPaymentSnapshotDto {

    private Long id;

    @JsonProperty("pa_fiscal_code")
    private String paFiscalCode;

    @JsonProperty("notice_id")
    private String noticeId;

    @JsonProperty("creditor_reference_id")
    private String creditorReferenceId;

    @JsonProperty("payment_token")
    private String paymentToken;

    private String status;

    @JsonProperty("inserted_timestamp")
    private Instant insertedTimestamp;

    @JsonProperty("updated_timestamp")
    private Instant updatedTimestamp;

    @JsonProperty("fk_position_payment")
    private Long fkPositionPayment;

    @JsonProperty("inserted_by")
    private String insertedBy;

    @JsonProperty("updated_by")
    private String updatedBy;
}
