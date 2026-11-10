package it.gov.pagopa.nodetsworker.observability.service;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Resolves the calling identity. Authentication is deferred (the worker runs behind APIM), so a single
 * implicit identity is returned; this is the one place to wire real roles/visibility later.
 */
@ApplicationScoped
public class CallerResolver {

  public Caller resolve() {
    return Caller.implicit();
  }
}
