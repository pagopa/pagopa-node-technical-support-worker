package it.gov.pagopa.nodetsworker.entities;


import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RegisterForReflection
public class StatiRPTSnapshotPK implements Serializable {

    @Column(name = "ID_DOMINIO")
    private String organizationFiscalCode;

    @Column(name = "IUV")
    private String iuv;

    @Column(name = "CCP")
    private String ccp;
}
