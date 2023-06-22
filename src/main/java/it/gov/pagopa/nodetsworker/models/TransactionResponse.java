package it.gov.pagopa.nodetsworker.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    @JsonProperty("data")
    private List<? extends BasePaymentInfo> payments;
}
