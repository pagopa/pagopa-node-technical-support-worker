package it.gov.pagopa.nodetsworker.observability.re;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import java.util.List;

/** Prevent automatic HTTP spans from capturing even rejected RE query values. */
public class ReTelemetryPrivacy implements AutoConfigurationCustomizerProvider {
  @Override
  public void customize(AutoConfigurationCustomizer customizer) {
    customizer.addSamplerCustomizer((delegate, properties) -> new PrivateSampler(delegate));
  }

  static final class PrivateSampler implements Sampler {
    private static final List<AttributeKey<String>> TARGETS = List.of(
        AttributeKey.stringKey("http.target"), AttributeKey.stringKey("http.url"),
        AttributeKey.stringKey("url.path"), AttributeKey.stringKey("url.full"),
        AttributeKey.stringKey("http.route"));
    private final Sampler delegate;

    PrivateSampler(Sampler delegate) {
      this.delegate = delegate;
    }

    @Override
    public SamplingResult shouldSample(Context parent, String traceId, String name, SpanKind kind,
        Attributes attributes, List<LinkData> links) {
      for (var key : TARGETS) {
        // Instrumentation attributes can contain raw targets or already-decoded paths.
        if (ReDiagnosticPaths.suppressRawTarget(attributes.get(key))
            || ReDiagnosticPaths.suppressDecodedPath(attributes.get(key))) {
          return SamplingResult.drop();
        }
      }
      if (ReDiagnosticPaths.suppressRawTarget(name) || ReDiagnosticPaths.suppressDecodedPath(name)) {
        return SamplingResult.drop();
      }
      return delegate.shouldSample(parent, traceId, name, kind, attributes, links);
    }

    @Override
    public String getDescription() {
      return "ReDiagnosticPrivacy(" + delegate.getDescription() + ")";
    }
  }
}
