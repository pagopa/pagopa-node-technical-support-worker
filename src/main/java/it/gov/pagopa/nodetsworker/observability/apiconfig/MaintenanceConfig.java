package it.gov.pagopa.nodetsworker.observability.apiconfig;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MaintenanceConfig {

  @ConfigProperty(name = "observability.maintenance.window-days", defaultValue = "7")
  long windowDays;

  /** A secret: never log or expose it. */
  @ConfigProperty(name = "observability.maintenance.subscription-key")
  Optional<String> subscriptionKey = Optional.empty();

  @ConfigProperty(name = "observability.maintenance.preload-on-startup", defaultValue = "true")
  boolean preloadOnStartup;

  public long windowDays() {
    return windowDays;
  }

  public Optional<String> subscriptionKey() {
    return subscriptionKey.filter(key -> !key.isBlank());
  }

  public boolean preloadOnStartup() {
    return preloadOnStartup;
  }
}
