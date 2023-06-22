package it.gov.pagopa.nodetsworker.entities;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.math.BigInteger;

@Getter
@Setter
@Entity
@Table(name = "POSITION_SERVICE")
@RegisterForReflection
public class PositionService extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "position_service_seq")
    @SequenceGenerator(
            name = "position_service_seq",
            sequenceName = "position_service_seq",
            allocationSize = 1)
    @Column(name = "ID", nullable = false)
    private BigInteger id;

    @Column(name = "PA_FISCAL_CODE")
    private String organizationFiscalCode;

    @Column(name = "COMPANY_NAME")
    private String organizationName;

    @Column(name = "NOTICE_ID")
    private String noticeNumber;
}
