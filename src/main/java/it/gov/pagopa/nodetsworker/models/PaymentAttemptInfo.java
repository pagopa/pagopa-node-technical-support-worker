package it.gov.pagopa.nodetsworker.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PaymentAttemptInfo extends BasePaymentAttemptInfo {

    private String paymentToken;
    private Boolean isOldPaymentModel = false;

    private Boolean pmReceipt;
    private String paymentMethod;
    private String paymentChannel;
    private Boolean flagPayPal;
    private Long stationVersion;
    private Long fee;
    private Long feeSpo;
    private Long feeOrganization;
    private String bundleId;
    private String bundleOrganizationId;
    private LocalDate applicationDate;
    private LocalDate transferDate;
}
