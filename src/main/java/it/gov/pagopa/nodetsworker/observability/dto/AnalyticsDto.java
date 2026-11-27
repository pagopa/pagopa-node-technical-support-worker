package it.gov.pagopa.nodetsworker.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Activations with a positive outcome versus payment outcomes received, over one minute-aligned window
 * and the same dimension filters. Without anomalies the two totals are nearly identical.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ActivationsOutcomes", requiredProperties = {"window", "live", "activatePaymentNoticeOk",
    "sendPaymentOutcome", "tokens", "coverage", "difference", "freshness", "generatedAt"}, additionalProperties = Schema.False.class)
public class AnalyticsDto {

  /** Effective window: {@code from} truncated to the minute, {@code to} rounded up; {@code to} is null when live. */
  private WindowDto window;

  private boolean live;

  @Schema(description = "Payment attempts (tokens) created by activatePaymentNotice/V2, i.e. activations with"
      + " outcome OK: RE payment_PAYING with that tipoEvento, counted once per token in its minute")
  private ActivationsDto activatePaymentNoticeOk;

  @Schema(description = "sendPaymentOutcome/V2 requests (REQ) received, any outcome, deduplicated,"
      + " in the minute of the request")
  private OutcomesDto sendPaymentOutcome;

  @Schema(nullable = true, description = "Per-token breakdown of activatePaymentNoticeOk (same tokens, window and"
      + " filters) by last observed state; null when coverage.status is UNAVAILABLE")
  private TokensDto tokens;

  private CoverageDto coverage;

  @Schema(description = "sendPaymentOutcome.total - activatePaymentNoticeOk.total: negative when outcomes are"
      + " missing. Raw indicator: requests and tokens are not 1:1 (a V2 request may carry up to 5 tokens, a"
      + " rejected late request is counted); tokens is the per-token figure")
  private long difference;

  private FreshnessDto freshness;

  private String generatedAt;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "ActivationCounts", requiredProperties = {"total", "activatePaymentNotice",
      "activatePaymentNoticeV2"}, additionalProperties = Schema.False.class)
  public static class ActivationsDto {
    private long total;
    private long activatePaymentNotice;
    private long activatePaymentNoticeV2;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "OutcomeCounts", requiredProperties = {"total", "sendPaymentOutcome", "sendPaymentOutcomeV2"},
      additionalProperties = Schema.False.class)
  public static class OutcomesDto {
    private long total;
    private long sendPaymentOutcome;
    private long sendPaymentOutcomeV2;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "Mod3CancelCounts", requiredProperties = {"total", "mod3Cancel", "mod3CancelV2"},
      additionalProperties = Schema.False.class)
  public static class CancellationsDto {
    private long total;
    private long mod3Cancel;
    private long mod3CancelV2;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "ActivatedTokens", description = "Each token counted once by its last observed state:"
      + " observed data, not necessarily the real state (see coverage). total = activatePaymentNoticeOk.total ="
      + " sum of the five categories", requiredProperties = {"total", "acceptedOutcome", "cancelledByMod3Cancel",
      "otherCancelled", "nonTerminal", "otherTerminal"}, additionalProperties = Schema.False.class)
  public static class TokensDto {
    private long total;

    @Schema(description = "Tokens with at least one state transition triggered by sendPaymentOutcome/V2 (an"
        + " accepted outcome, OK or KO); V2 when any such transition is V2")
    private OutcomesDto acceptedOutcome;

    @Schema(description = "No accepted outcome; CANCELLED through the mod3Cancel/mod3CancelV2 expiry job,"
        + " whenever it happened, also after the window")
    private CancellationsDto cancelledByMod3Cancel;

    @Schema(description = "No accepted outcome; CANCELLED by another cause")
    private ByCauseDto otherCancelled;

    @Schema(description = "No accepted outcome; last observed state not final in the attempt FSM: the final"
        + " state has not been observed (not necessarily a payment in progress)")
    private NonTerminalDto nonTerminal;

    @Schema(description = "No accepted outcome; final state other than CANCELLED")
    private ByStateDto otherTerminal;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "TokensByCause", requiredProperties = {"total", "byCause"}, additionalProperties = Schema.False.class)
  public static class ByCauseDto {
    private long total;

    @Schema(description = "Sorted by count, descending")
    private java.util.List<Bucket> byCause;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "TokensByState", requiredProperties = {"total", "byState"}, additionalProperties = Schema.False.class)
  public static class ByStateDto {
    private long total;

    @Schema(description = "Sorted by count, descending")
    private java.util.List<Bucket> byState;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "TokensByObservedState", requiredProperties = {"total", "byState"},
      additionalProperties = Schema.False.class)
  public static class NonTerminalDto {
    private long total;

    @Schema(description = "One item per last observed state, sorted by count, descending")
    private java.util.List<StateCount> byState;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "TokenStateCount", requiredProperties = {"state", "count"}, additionalProperties = Schema.False.class)
  public static class StateCount {
    private String state;
    private long count;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "TokenBucket", requiredProperties = {"cause", "count"}, additionalProperties = Schema.False.class)
  public static class Bucket {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    @Schema(description = "Last observed state (byState only)")
    private String state;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    @Schema(nullable = true, description = "tipoEvento of the transition into the last observed state; null"
        + " when not recorded")
    private String cause;

    private long count;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(name = "AnalyticsCoverage", requiredProperties = {"status", "reasons", "lastObservedEventAt"},
      additionalProperties = Schema.False.class)
  public static class CoverageDto {
    @Schema(enumeration = {"UNKNOWN", "UNAVAILABLE"}, description = "Completeness is never asserted: UNKNOWN"
        + " when no reliable ingestion watermark proves the timeline complete, UNAVAILABLE when tokens may"
        + " already be deleted by retention (tokens is null)")
    private String status;

    @Schema(description = "NO_INGESTION_WATERMARK, RETENTION")
    private java.util.List<String> reasons;

    @Schema(nullable = true, description = "Source time of the latest projected attempt state event: observed"
        + " data, not a coverage guarantee")
    private String lastObservedEventAt;
  }
}
