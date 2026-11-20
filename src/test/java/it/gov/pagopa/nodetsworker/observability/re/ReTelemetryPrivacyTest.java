package it.gov.pagopa.nodetsworker.observability.re;

import static org.junit.jupiter.api.Assertions.*;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class ReTelemetryPrivacyTest {
  @Test
  void checksPathsWithRepeatedSlashesInLinearTime() {
    String path = "/api/attempts/synthetic/re-events/" + "/".repeat(50_000) + "x";

    assertTimeoutPreemptively(Duration.ofSeconds(2),
        () -> assertFalse(ReDiagnosticPaths.suppressDecodedPath(path)));
  }

  @Test
  void dropsDiagnosticHttpTargetsEvenWhenTheyContainRejectedSecretQueryParameters() {
    var sampler = new ReTelemetryPrivacy.PrivateSampler(Sampler.alwaysOn());
    for (String attribute : List.of("http.target", "http.url", "url.path", "url.full", "http.route")) {
      var attributes = Attributes.of(AttributeKey.stringKey(attribute),
          "/api/attempts/synthetic/re-events?token=synthetic-secret");
      assertEquals(SamplingDecision.DROP, sampler.shouldSample(Context.root(), "0".repeat(32),
          "POST", SpanKind.SERVER, attributes, List.of()).getDecision());
    }
    assertEquals(SamplingDecision.RECORD_AND_SAMPLE, sampler.shouldSample(Context.root(), "0".repeat(32),
        "GET", SpanKind.SERVER, Attributes.of(AttributeKey.stringKey("http.target"), "/api/payments"),
        List.of()).getDecision());
  }

  @Test
  void autoconfigurationLoadsPrivacyWrapperAndPreservesConfiguredDelegate() {
    assertTrue(ServiceLoader.load(AutoConfigurationCustomizerProvider.class).stream()
        .anyMatch(provider -> provider.type().equals(ReTelemetryPrivacy.class)));
    var sdk = AutoConfiguredOpenTelemetrySdk.builder().registerShutdownHook(false).setResultAsGlobal(false)
        .addPropertiesSupplier(() -> Map.of("otel.traces.exporter", "none",
            "otel.metrics.exporter", "none", "otel.logs.exporter", "none",
            "otel.traces.sampler", "always_on")).build().getOpenTelemetrySdk();
    try {
      var span = sdk.getTracer("synthetic").spanBuilder("POST").setSpanKind(SpanKind.SERVER)
          .setAttribute("http.target", "/api/attempts/synthetic/re-events?token=synthetic-secret").startSpan();
      assertFalse(span.isRecording());
      assertFalse(span.getSpanContext().isSampled());
      span.end();
      var normal = sdk.getTracer("synthetic").spanBuilder("normal").startSpan();
      assertTrue(normal.isRecording());
      normal.end();
    } finally {
      sdk.close();
    }
  }

  @Test
  void dropsMatrixAndEncodedRouteVariantsThroughTheActualAutoconfigurationSpi() {
    var sdk = AutoConfiguredOpenTelemetrySdk.builder().registerShutdownHook(false).setResultAsGlobal(false)
        .addPropertiesSupplier(() -> Map.of("otel.traces.exporter", "none",
            "otel.metrics.exporter", "none", "otel.logs.exporter", "none",
            "otel.traces.sampler", "always_on")).build().getOpenTelemetrySdk();
    try {
      for (String target : List.of(
          "/api/attempts/synthetic/re-events;v=1?token=synthetic-secret",
          "/api/attempts/synthetic;v=1/re-events;v=2?token=synthetic-secret",
          "/api/attempts/synthetic/re-events%3Bv=1?token=synthetic-secret",
          "/api/attempts/synthetic/re%2Devents%3bv=1%3Ftoken=synthetic-secret",
          "/api/attempts/synthetic%3Fsecret/re%2Devents",
          "/api/attempts/synthetic%23secret/re%2Devents",
          "/api/attempts/synthetic?secret/re-events",
          "/api/attempts/synthetic#secret/re-events",
          "%2Fapi%2Fattempts%2Fsynthetic%2Fre-events%3Bv%3D1%3Ftoken=synthetic-secret",
          "/api/%61ttempts/synthetic/re-events%253Bv=1%253Ftoken=synthetic-secret",
          "https://synthetic.invalid/api/attempts/synthetic/re-events;v=1?token=synthetic-secret")) {
        for (String attribute : List.of("http.target", "http.url", "url.path", "url.full", "http.route")) {
          var span = sdk.getTracer("synthetic").spanBuilder("POST").setSpanKind(SpanKind.SERVER)
              .setAttribute(attribute, target).startSpan();
          assertFalse(span.isRecording(), attribute + ": " + target);
          assertFalse(span.getSpanContext().isSampled(), attribute + ": " + target);
          span.end();
        }
      }
    } finally {
      sdk.close();
    }
  }
}
