package it.gov.pagopa.nodetsworker.observability.auth;

import jakarta.enterprise.context.RequestScoped;

/** The user authenticated by {@link AuthFilter} for the current request, {@code null} when none. */
@RequestScoped
public class AuthenticatedUser {

  private EntraUser user;

  public EntraUser get() {
    return user;
  }

  void set(EntraUser user) {
    this.user = user;
  }
}
