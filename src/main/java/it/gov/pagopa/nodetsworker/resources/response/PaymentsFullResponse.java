package it.gov.pagopa.nodetsworker.resources.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.gov.pagopa.nodetsworker.models.PaymentFullInfo;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentsFullResponse {

  private LocalDate dateFrom;
  private LocalDate dateTo;

  private int count;

  @JsonProperty("data")
  private List<PaymentFullInfo> payments;
}
