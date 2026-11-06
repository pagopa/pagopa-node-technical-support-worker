package it.gov.pagopa.nodetsworker.observability.dto;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** OpenAPI shapes for the map-based outbox protocol; each SSE data field contains one such object. */
public final class SseContract {
  private SseContract() {
  }

  @Schema(name = "ReadyEvent", requiredProperties = {"type", "scope", "projectionRevision"},
      additionalProperties = Schema.False.class)
  public static class Ready {
    @Schema(enumeration = {"ready"}) public String type;
    public Map<String, String> scope;
    public long projectionRevision;
    public WindowDto window;
  }

  @Schema(name = "PaymentDelta", requiredProperties = {"type", "scope", "paFiscalCode",
      "noticeNumber", "projectionRevision", "changes"}, additionalProperties = Schema.False.class)
  public static class PaymentDelta {
    @Schema(enumeration = {"payment.delta"}) public String type;
    public Map<String, String> scope;
    public String paFiscalCode;
    public String noticeNumber;
    public long projectionRevision;
    public PaymentChanges changes;
  }

  @Schema(name = "PaymentChanges", requiredProperties = {"logicalTransitions", "attemptChanges",
      "removedAttemptIds", "deleted"}, additionalProperties = Schema.False.class,
      description = "Recipient changes/removals are independent of attempts. Omitted optional arrays mean no change, not unsupported recipients.")
  public static class PaymentChanges {
    public List<TimelineItemDto> logicalTransitions;
    public List<AttemptChange> attemptChanges;
    public List<String> removedAttemptIds;
    public List<RecipientChange> recipientChanges;
    public List<String> removedRecipientIds;
    public boolean deleted;
    public Boolean timelineIncomplete;
  }

  @Schema(name = "RecipientChange", requiredProperties = {"recipientId"},
      additionalProperties = Schema.False.class)
  public static class RecipientChange {
    public String recipientId;
    public List<TimelineItemDto> transitions;
    public RecipientDto upsert;
    public RecipientPatch patch;
  }

  @Schema(name = "RecipientPatch", additionalProperties = Schema.False.class)
  public static class RecipientPatch {
    public Boolean timelineIncomplete;
  }

  @Schema(name = "AttemptChange", requiredProperties = {"attemptId", "warning"},
      additionalProperties = Schema.False.class)
  public static class AttemptChange {
    public String attemptId;
    public boolean warning;
    public List<TimelineItemDto> transitions;
    public AttemptDto upsert;
    public AttemptPatch patch;
    public List<TransitionCorrection> transitionCorrections;
  }

  @Schema(name = "AttemptPatch", additionalProperties = Schema.False.class)
  public static class AttemptPatch {
    public String serviceIdentifier;
    public String psp;
    public String canale;
    public String stazione;
    @Schema(nullable = true, format = "date-time") public String attemptStartedAt;
    public TimestampBasis startTimestampBasis;
    public Boolean timelineIncomplete;
  }

  @Schema(name = "TransitionCorrection", requiredProperties = {"index", "transition"},
      additionalProperties = Schema.False.class)
  public static class TransitionCorrection {
    @Schema(minimum = "0") public int index;
    public TimelineItemDto transition;
  }

  @Schema(name = "AggregateDelta", requiredProperties = {"type", "scope", "projectionRevision",
      "changes", "freshness"}, additionalProperties = Schema.False.class)
  public static class AggregateDelta {
    @Schema(enumeration = {"aggregate.delta"}) public String type;
    public Map<String, String> scope;
    public long projectionRevision;
    public AggregateChanges changes;
    public FreshnessDto freshness;
  }

  @Schema(name = "AggregateChanges", requiredProperties = {"counts", "logicalStates", "attemptStates",
      "recipientStates", "traffic", "transitions", "matrices", "anomalies", "durations", "durationSnapshot",
      "primitives"},
      additionalProperties = Schema.False.class,
      description = "Signed additive differences; durationSnapshot is the authoritative replacement, including an empty array.")
  public static class AggregateChanges {
    public DeltaCounts counts;
    public List<StateCountDto> logicalStates;
    public List<StateCountDto> attemptStates;
    public List<StateCountDto> recipientStates;
    public List<TrafficBucketDto> traffic;
    public List<TransitionTrendDto> transitions;
    public DeltaMatrices matrices;
    public AnomaliesDto anomalies;
    public List<DurationDelta> durations;
    public List<DurationRowDto> durationSnapshot;
    public List<PrimitiveCountDto> primitives;
  }

  @Schema(name = "DeltaCounts", requiredProperties = {"payments", "attempts", "unresolvedPayments",
      "unresolvedAttempts", "anomalyTransitions"}, additionalProperties = Schema.False.class)
  public static class DeltaCounts {
    public long payments;
    public long attempts;
    public long unresolvedPayments;
    public long unresolvedAttempts;
    public long anomalyTransitions;
  }

  @Schema(name = "DeltaMatrices", requiredProperties = {"psp", "canale", "recipient"},
      additionalProperties = Schema.False.class)
  public static class DeltaMatrices {
    public List<DimensionDelta> psp;
    public List<DimensionDelta> canale;
    public List<DimensionDelta> recipient;
  }

  @Schema(name = "DimensionDelta", requiredProperties = {"value", "state", "count"},
      additionalProperties = Schema.False.class)
  public static class DimensionDelta {
    @Schema(nullable = true) public String value;
    public String state;
    public long count;
  }

  @Schema(name = "DurationDelta", requiredProperties = {"entity", "fromState", "toState",
      "timestampBasis", "samples", "totalMs"}, additionalProperties = Schema.False.class)
  public static class DurationDelta {
    @Schema(enumeration = {"logical", "attempt", "recipient"}) public String entity;
    public String fromState;
    public String toState;
    public TimestampBasis timestampBasis;
    public long samples;
    public double totalMs;
  }
}
