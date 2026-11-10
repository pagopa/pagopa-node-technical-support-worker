package it.gov.pagopa.nodetsworker.observability.monitoring;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Monitoring targets queried for the cache version, in the order they are reported. */
@ApplicationScoped
public class MonitoringConfig {

  @ConfigProperty(name = "observability.monitoring.timeout-seconds", defaultValue = "6")
  long timeoutSeconds;

  @ConfigProperty(name = "observability.monitoring.ndp001.target", defaultValue = "NDP001DEV")
  String ndp001Target;

  @ConfigProperty(name = "observability.monitoring.ndp001.subscription-key")
  Optional<String> ndp001SubscriptionKey = Optional.empty();

  @ConfigProperty(name = "observability.monitoring.ndp004.target", defaultValue = "NDP004IT")
  String ndp004Target;

  @ConfigProperty(name = "observability.monitoring.ndp004.subscription-key")
  Optional<String> ndp004SubscriptionKey = Optional.empty();

  public long timeoutSeconds() {
    return timeoutSeconds;
  }

  public List<Node> nodes() {
    return List.of(
        new Node("NDP001", ndp001Target, ndp001SubscriptionKey.filter(key -> !key.isBlank())),
        new Node("NDP004", ndp004Target, ndp004SubscriptionKey.filter(key -> !key.isBlank())));
  }

  /** The subscription key is a secret: never log or expose it. */
  public record Node(String name, String target, Optional<String> subscriptionKey) {
  }
}
