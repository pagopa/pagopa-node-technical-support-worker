package it.gov.pagopa.nodetsworker.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionAttemptResponse {

    private LocalDate dateFrom;
    private LocalDate dateTo;
    @JsonProperty("data")
    private List paymentAttemptInfoList;
}
