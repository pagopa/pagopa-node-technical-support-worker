package it.gov.pagopa.nodetsworker.observability.auth;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Microsoft Entra ID settings; fails fast at startup when authentication is misconfigured. */
@ApplicationScoped
public class AuthConfig {

  @ConfigProperty(name = "observability.auth.enabled", defaultValue = "true")
  boolean enabled;

  @ConfigProperty(name = "observability.auth.entra.tenant-id")
  Optional<String> tenantId;

  @ConfigProperty(name = "observability.auth.entra.client-id")
  Optional<String> clientId;

  @ConfigProperty(name = "observability.auth.entra.authority-host",
      defaultValue = "https://login.microsoftonline.com")
  String authorityHost;

  void validate(@Observes StartupEvent event) {
    if (!enabled) {
      return;
    }
    if (tenantId().isBlank()) {
      throw new IllegalStateException("observability.auth.entra.tenant-id (ENTRA_TENANT_ID) is required");
    }
    if (clientId().isBlank()) {
      throw new IllegalStateException("observability.auth.entra.client-id (ENTRA_CLIENT_ID) is required");
    }
  }

  public boolean enabled() {
    return enabled;
  }

  public String tenantId() {
    return tenantId.map(String::trim).orElse("");
  }

  public String clientId() {
    return clientId.map(String::trim).orElse("");
  }

  /** Issuer of v2.0 tokens of the tenant, e.g. {@code https://login.microsoftonline.com/<tenant>/v2.0}. */
  public String issuer() {
    return authority() + "/v2.0";
  }

  /** Signing keys of the tenant, as published by its OpenID Connect metadata ({@code jwks_uri}). */
  public String jwksUrl() {
    return authority() + "/discovery/v2.0/keys";
  }

  private String authority() {
    String host = authorityHost.endsWith("/") ? authorityHost.substring(0, authorityHost.length() - 1) : authorityHost;
    return host + "/" + tenantId();
  }
}
