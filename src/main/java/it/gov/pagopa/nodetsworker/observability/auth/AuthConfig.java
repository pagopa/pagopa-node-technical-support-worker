package it.gov.pagopa.nodetsworker.observability.auth;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Authentication switch; the Entra ID token itself is validated upstream by Azure API Management. */
@ApplicationScoped
public class AuthConfig {

  @ConfigProperty(name = "observability.auth.enabled", defaultValue = "true")
  boolean enabled;

  public boolean enabled() {
    return enabled;
  }
}
