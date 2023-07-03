package it.gov.pagopa.nodetsworker.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PaymentAttemptInfo extends BasePaymentAttemptInfo {

    private String paymentToken;
    private Boolean pmReceipt;
    private String paymentMethod;
    private String paymentChannel;
    private Long stationVersion;
    private BigDecimal fee;
    private BigDecimal feeOrganization;
    private String bundleId;
    private String bundleOrganizationId;
    private String applicationDate;
    private String transferDate;
}
