package it.gov.pagopa.nodetsworker.entities;


import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import it.gov.pagopa.nodetsworker.mappers.YesNoConverter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "RPT")
@RegisterForReflection
public class RPT extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rpt_seq")
    @SequenceGenerator(
            name = "rpt_seq",
            sequenceName = "rpt_seq",
            allocationSize = 1)
    @Column(name = "ID", nullable = false, columnDefinition = "NUMERIC")
    private Long id;

    @Column(name = "IDENT_DOMINIO")
    private String organizationFiscalCode;

    @Column(name = "IUV")
    private String iuv;

    @Column(name = "CCP")
    private String ccp;

    @Column(name = "BIC_ADDEBITO")
    private String bic;

    @Column(name = "PSP")
    private String pspId;

    @Column(name = "INTERMEDIARIOPSP")
    private String brokerPspId;

    @Column(name = "CANALE")
    private String channelId;

    @Column(name = "DATA_MSG_RICH")
    private LocalDateTime paymentRequestTimestamp;

    @Column(name = "FLAG_CANC", columnDefinition = "bpchar")
    @Convert(converter = YesNoConverter.class)
    private Boolean revokeRequest;

    @Column(name = "INSERTED_TIMESTAMP")
    private LocalDateTime insertedTimestamp;

    @Column(name = "UPDATED_TIMESTAMP")
    private LocalDateTime updatedTimestamp;

    @Column(name = "NUM_VERSAMENTI", columnDefinition = "NUMERIC")
    private Long numberOfPayments;

    @Column(name = "SOMMA_VERSAMENTI", columnDefinition = "FLOAT4")
    private Double amount;

    @Column(name = "WISP_2", columnDefinition = "bpchar")
    @Convert(converter = YesNoConverter.class)
    private Boolean wispInitialization;

    @Column(name = "FLAG_SECONDA", columnDefinition = "bpchar")
    @Convert(converter = YesNoConverter.class)
    private Boolean retriedRPT;

    @Column(name = "FLAG_IO", columnDefinition = "bpchar")
    @Convert(converter = YesNoConverter.class)
    private Boolean flagIO;

}
