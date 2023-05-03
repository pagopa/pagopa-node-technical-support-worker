package it.gov.pagopa.nodetsworker.models;

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
    private List<PaymentInfo> paymentInfoList;
}
