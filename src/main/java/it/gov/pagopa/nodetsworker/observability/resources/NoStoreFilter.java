package it.gov.pagopa.nodetsworker.observability.resources;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Adds {@code Cache-Control: no-store} and {@code X-Content-Type-Options: nosniff} to {@link NoStore} endpoints. */
@Provider
@NoStore
public class NoStoreFilter implements ContainerResponseFilter {

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    response.getHeaders().putSingle("Cache-Control", "no-store");
    response.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
  }
}
