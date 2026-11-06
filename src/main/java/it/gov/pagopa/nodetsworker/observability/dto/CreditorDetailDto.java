package it.gov.pagopa.nodetsworker.observability.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Contract schema {@code CreditorDetail}: the aggregate of a single creditor, with freshness. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(requiredProperties = {"paFiscalCode", "snapshotAt", "projectionRevision", "window", "counts",
    "logicalStates", "attemptStates", "recipientStates", "unresolved", "matrices", "traffic",
    "transitions", "durations", "anomalies", "freshness"}, additionalProperties = Schema.False.class)
@JsonPropertyOrder({"paFiscalCode", "snapshotAt", "projectionRevision", "window", "counts",
    "logicalStates", "attemptStates", "recipientStates", "unresolved", "matrices", "traffic",
    "transitions", "durations", "anomalies", "freshness"})
public class CreditorDetailDto extends FreshAggregateDto {
  private String paFiscalCode;
}
