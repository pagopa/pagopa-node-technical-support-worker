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
import javax.persistence.IdClass;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "RT")
@RegisterForReflection
public class RT extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rt_seq")
    @SequenceGenerator(
            name = "rt_seq",
            sequenceName = "rt_seq",
            allocationSize = 1)
    @Column(name = "ID", nullable = false, columnDefinition = "NUMERIC")
    private Long id;

    @Column(name = "IDENT_DOMINIO")
    private String organizationFiscalCode;

    @Column(name = "IUV")
    private String iuv;

    @Column(name = "CCP")
    private String ccp;

    @Column(name = "ESITO")
    private String outcome;

}
