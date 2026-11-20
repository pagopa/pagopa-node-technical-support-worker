package it.gov.pagopa.nodetsworker.observability.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Tunable limits of the observability API, mirroring the functional contract defaults. */
@ApplicationScoped
public class ObservabilityConfig {

  @ConfigProperty(name = "observability.window.default-minutes", defaultValue = "60")
  long windowDefaultMinutes;

  @ConfigProperty(name = "observability.window.max-days", defaultValue = "60")
  long windowMaxDays;

  @ConfigProperty(name = "observability.page.default-size", defaultValue = "20")
  int pageDefaultSize;

  @ConfigProperty(name = "observability.page.max-size", defaultValue = "100")
  int pageMaxSize;

  @ConfigProperty(name = "observability.cursor.ttl-seconds", defaultValue = "300")
  long cursorTtlSeconds;

  @ConfigProperty(name = "observability.sse.heartbeat-seconds", defaultValue = "15")
  long sseHeartbeatSeconds;

  @ConfigProperty(name = "observability.sse.replay-limit", defaultValue = "100")
  int sseReplayLimit;

  @ConfigProperty(name = "observability.sse.replay-ttl-seconds", defaultValue = "300")
  long sseReplayTtlSeconds;

  @ConfigProperty(name = "observability.retention.days", defaultValue = "60")
  long retentionDays;

  public long windowDefaultMinutes() {
    return windowDefaultMinutes;
  }

  public long windowMaxDays() {
    return windowMaxDays;
  }

  public int pageDefaultSize() {
    return pageDefaultSize;
  }

  public int pageMaxSize() {
    return pageMaxSize;
  }

  public long cursorTtlSeconds() {
    return cursorTtlSeconds;
  }

  public long sseHeartbeatSeconds() {
    return sseHeartbeatSeconds;
  }

  public int sseReplayLimit() {
    return sseReplayLimit;
  }

  public long sseReplayTtlSeconds() {
    return sseReplayTtlSeconds;
  }

  public long retentionDays() {
    return retentionDays;
  }
}
