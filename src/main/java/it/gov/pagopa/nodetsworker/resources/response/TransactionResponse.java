package it.gov.pagopa.nodetsworker.resources.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import it.gov.pagopa.nodetsworker.models.BasePaymentInfo;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse<T extends BasePaymentInfo> {

  private LocalDate dateFrom;
  private LocalDate dateTo;

  private int count;

  @JsonProperty("data")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = BasePaymentInfo.class, name = "car"),
    @JsonSubTypes.Type(value = BasePaymentInfo.class, name = "truck")
  })
  private List<T> payments;
}
