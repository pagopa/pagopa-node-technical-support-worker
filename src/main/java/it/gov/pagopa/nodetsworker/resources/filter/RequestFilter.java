package it.gov.pagopa.nodetsworker.resources.filter;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import it.gov.pagopa.nodetsworker.observability.re.ReDiagnosticPaths;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.UUID;

@Provider
public class RequestFilter implements ContainerRequestFilter {

  @Inject Logger log;

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    // RE has a dedicated metadata-only audit; even rejected query parameters can contain secrets.
    if (ReDiagnosticPaths.suppressDecodedPath(containerRequestContext.getUriInfo().getPath())) {
      return;
    }
    long requestStartTime = System.nanoTime();
    containerRequestContext.setProperty("requestStartTime", requestStartTime);

    String requestMethod = containerRequestContext.getMethod();
    String requestPath = containerRequestContext.getUriInfo().getPath();

    MultivaluedMap<String, String> queryParameters =
        containerRequestContext.getUriInfo().getQueryParameters();

    log.infof("REQ --> %s [uri:%s] [params:%s]", requestMethod, requestPath, queryParameters);
  }
}
