package it.gov.pagopa.nodetsworker.entities;


import io.quarkus.arc.All;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import it.gov.pagopa.nodetsworker.mappers.YesNoConverter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "POSITION_PAYMENT")
@RegisterForReflection
public class PositionPayment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "position_payment_seq")
    @SequenceGenerator(
            name = "position_payment_seq",
            sequenceName = "position_payment_seq",
            allocationSize = 1)
    @Column(name = "ID", nullable = false, columnDefinition = "NUMERIC")
    private Long id;

    @Column(name = "PA_FISCAL_CODE")
    private String organizationFiscalCode;

    @Column(name = "NOTICE_ID")
    private String noticeNumber;

    @Column(name = "CREDITOR_REFERENCE_ID")
    private String creditorReferenceId;

    @Column(name = "PAYMENT_TOKEN")
    private String paymentToken;

    @Column(name = "PSP_ID")
    private String pspId;

    @Column(name = "BROKER_PSP_ID")
    private String brokerPspId;

    @Column(name = "CHANNEL_ID")
    private String channelId;

    @Column(name = "OUTCOME")
    private String outcome;

    @Column(name = "TRANSFER_DATE")
    private LocalDate transferDate;

    @Column(name = "PAYER_ID", columnDefinition = "NUMERIC")
    private Long payerId;

    @Column(name = "RPT_ID", columnDefinition = "NUMERIC")
    private Long rptId;

    @Column(name = "APPLICATION_DATE")
    private LocalDate applicationDate;

    @Column(name = "INSERTED_TIMESTAMP")
    private LocalDateTime insertedTimestamp;

    @Column(name = "UPDATED_TIMESTAMP")
    private LocalDateTime updatedTimestamp;

    @Column(name = "RICEVUTA_PM", columnDefinition = "bpchar")
    @Convert(converter = YesNoConverter.class)
    private Boolean pmReceipt;

    @Column(name = "PAYMENT_METHOD")
    private String paymentMethod;

    @Column(name = "PAYMENT_CHANNEL")
    private String paymentChannel;

    @Column(name = "FLAG_IO", columnDefinition = "bpchar")
    @Convert(converter = YesNoConverter.class)
    private Boolean flagIO;

    @Column(name = "FLAG_PAYPAL", columnDefinition = "bpchar")
    @Convert(converter = YesNoConverter.class)
    private Boolean flagPayPal;

    @Column(name = "BROKER_PA_ID")
    private String brokerOrganizationId;

    @Column(name = "STATION_ID")
    private String stationId;

    @Column(name = "STATION_VERSION", columnDefinition = "NUMERIC")
    private Long stationVersion;

    @Column(name = "AMOUNT", columnDefinition = "NUMERIC")
    private Double amount;

    @Column(name = "FEE", columnDefinition = "NUMERIC")
    private Long fee;

    @Column(name = "FEE_SPO", columnDefinition = "NUMERIC")
    private Long feeSpo;

    @Column(name = "FEE_PA", columnDefinition = "NUMERIC")
    private Long feeOrganization;

    @Column(name = "BUNDLE_ID")
    private String bundleId;

    @Column(name = "BUNDLE_PA_ID")
    private String bundleOrganizationId;
}
