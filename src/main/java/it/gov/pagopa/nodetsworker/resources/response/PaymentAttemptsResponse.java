package it.gov.pagopa.nodetsworker.resources.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.gov.pagopa.nodetsworker.models.PaymentAttemptInfo;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAttemptsResponse {

  private LocalDate dateFrom;
  private LocalDate dateTo;

  private int count;

  @JsonProperty("data")
  private List<PaymentAttemptInfo> payments;
}
