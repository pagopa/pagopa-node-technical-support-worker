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
@Table(name = "STATI_RPT_SNAPSHOT")
@IdClass(StatiRPTSnapshotPK.class)
@RegisterForReflection
public class StatiRPTSnapshot extends PanacheEntityBase {

    @Id
    private StatiRPTSnapshotPK id;

    @Column(name = "STATO")
    private String status;

}
