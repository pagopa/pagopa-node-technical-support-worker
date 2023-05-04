package it.gov.pagopa.nodetsworker.entities;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "STATI_RPT_SNAPSHOT")
@RegisterForReflection
public class StatiRPTSnapshot extends PanacheEntityBase {

    @EmbeddedId
    private StatiRPTSnapshotPK id;

    @Column(name = "STATO")
    private String status;

    @Column(name = "INSERTED_TIMESTAMP")
    private LocalDateTime insertedTimestamp;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class StatiRPTSnapshotPK implements Serializable {

        @Column(name = "ID_DOMINIO")
        private String organizationFiscalCode;

        @Column(name = "IUV")
        private String iuv;

        @Column(name = "CCP")
        private String ccp;
    }
}
