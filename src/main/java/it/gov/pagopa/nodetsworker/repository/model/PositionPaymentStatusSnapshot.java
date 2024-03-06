package it.gov.pagopa.nodetsworker.repository.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@Entity
@Table(name = "POSITION_PAYMENT_STATUS_SNAPSHOT")
public class PositionPaymentStatusSnapshot extends PanacheEntity{

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
    private Instant inserted_timestamp;

    @Column(name = "UPDATED_TIMESTAMP")
    private Instant updatedTimestamp;

    @Column(name = "INSERTED_BY")
    private String insertedBy;

    @Column(name = "UPDATED_BY")
    private String updatedBY;

}
