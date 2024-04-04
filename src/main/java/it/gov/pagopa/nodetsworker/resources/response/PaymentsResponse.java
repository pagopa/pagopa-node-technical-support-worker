package it.gov.pagopa.nodetsworker.resources.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.gov.pagopa.nodetsworker.models.PaymentInfo;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentsResponse{

  private LocalDate dateFrom;
  private LocalDate dateTo;

  private int count;

  @JsonProperty("data")
  private List<PaymentInfo> payments;
}
