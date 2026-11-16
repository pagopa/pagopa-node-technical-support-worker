package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.auth.AuthConfig;
import it.gov.pagopa.nodetsworker.observability.auth.AuthenticatedUser;
import it.gov.pagopa.nodetsworker.observability.auth.EntraUser;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves the calling identity from the Entra ID token validated by the auth filter. Without role or
 * group restrictions every signed-in user is an operator; when authentication is disabled
 * (tests, local tooling) the implicit identity is returned.
 */
@ApplicationScoped
public class CallerResolver {

  @Inject
  AuthConfig authConfig;

  @Inject
  AuthenticatedUser authenticatedUser;

  public Caller resolve() {
    if (!authConfig.enabled()) {
      return Caller.implicit();
    }
    EntraUser user = authenticatedUser.get();
    if (user == null) {
      throw Errors.unauthorized("Autenticazione richiesta.");
    }
    return Caller.authenticated(user.email());
  }
}
