package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Contract schema {@code Overview}: an aggregate snapshot, pipeline freshness and primitive counters. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(requiredProperties = {"snapshotAt", "projectionRevision", "window", "counts", "logicalStates",
    "attemptStates", "recipientStates", "unresolved", "matrices", "traffic", "transitions",
    "durations", "anomalies", "freshness", "primitives"}, additionalProperties = Schema.False.class)
public class OverviewDto extends FreshAggregateDto {
  /** Primitive requests, responses and warnings per {@code serviceIdentifier} (spec §3.3.2). */
  private java.util.List<PrimitiveCountDto> primitives;
}
