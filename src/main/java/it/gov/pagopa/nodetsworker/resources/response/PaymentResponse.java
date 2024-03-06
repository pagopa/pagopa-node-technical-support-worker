package it.gov.pagopa.nodetsworker.resources.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Jacksonized
@JsonPropertyOrder({"metadata", "count", "data"})
public class PaymentResponse {

    private Metadata metadata;

    private LocalDate dateFrom;
    private LocalDate dateTo;

    @Schema(example = "100")
    private long count;

    private List<PositionPaymentSSInfo> data;

}
