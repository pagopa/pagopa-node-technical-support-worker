package it.gov.pagopa.nodetsworker.entities;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "POSITION_PAYMENT_STATUS_SNAPSHOT")
@RegisterForReflection
public class PositionPaymentStatusSnapshot extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "position_payment_seq")
    @SequenceGenerator(
            name = "position_payment_seq",
            sequenceName = "position_payment_seq",
            allocationSize = 1)
    @Column(name = "ID", nullable = false, columnDefinition = "NUMERIC")
    private Long id;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "FK_POSITION_PAYMENT", columnDefinition = "NUMERIC")
    private Long fkPositionPayment;

    @Column(name = "INSERTED_TIMESTAMP")
    private LocalDateTime insertedTimestamp;

}
