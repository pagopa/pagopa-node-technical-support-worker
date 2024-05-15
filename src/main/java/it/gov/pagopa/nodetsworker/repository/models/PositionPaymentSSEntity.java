package it.gov.pagopa.nodetsworker.repository.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "V_POSITION_PAYMENT_STATUS_SNAPSHOT")
public class PositionPaymentSSEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PA_FISCAL_CODE")
    private String paFiscalCode;

    @Column(name = "NOTICE_ID")
    private String noticeId;

    @Column(name = "CREDITOR_REFERENCE_ID")
    private String creditorReferenceId;

    @Column(name = "PAYMENT_TOKEN")
    private String paymentToken;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "INSERTED_TIMESTAMP")
    private Instant insertedTimestamp;

    @Column(name = "UPDATED_TIMESTAMP")
    private Instant updatedTimestamp;

    @Column(name = "FK_POSITION_PAYMENT")
    private Long fkPositionPayment;

    @Column(name = "INSERTED_BY")
    private String insertedBy;

    @Column(name = "UPDATED_BY")
    private String updatedBY;

}
