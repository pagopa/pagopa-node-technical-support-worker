package it.gov.pagopa.nodetsworker.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BasePaymentAttemptInfo extends BasePaymentInfo {

    private String brokerOrganizationId;
    private String stationId;
    private String paymentMethod;
    private Double amount;
    private Boolean flagIO;
}
