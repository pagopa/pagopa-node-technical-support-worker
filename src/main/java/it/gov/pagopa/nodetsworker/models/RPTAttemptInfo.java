package it.gov.pagopa.nodetsworker.models;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class RPTAttemptInfo extends BasePaymentAttemptInfo {

    private String ccp;
    private Long numberOfPayments;
    private Boolean retriedRPT;
    private Boolean isOldPaymentModel = true;
    private Boolean wispInitialization;
    private Boolean pmReceipt;
    @JsonProperty("iuv")
    private String noticeNumber;
}
