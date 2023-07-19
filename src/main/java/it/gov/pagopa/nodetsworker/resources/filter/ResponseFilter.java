package it.gov.pagopa.nodetsworker.resources.filter;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;
import org.slf4j.MDC;

@Provider
public class ResponseFilter implements ContainerResponseFilter {

  @Inject Logger log;

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    if (requestContext.getPropertyNames().contains("requestStartTime")) {
      long requestStartTime = (long) requestContext.getProperty("requestStartTime");
      long requestFinishTime = System.nanoTime();
      long elapsed = TimeUnit.NANOSECONDS.toMillis(requestFinishTime - requestStartTime);
      String requestPath = requestContext.getUriInfo().getAbsolutePath().getPath();
      String requestMethod = requestContext.getMethod();
      int httpStatus = responseContext.getStatus();
      log.infof(
          "RES --> %s [uri:%s] [elapsed:%dms] [statusCode:%d]",
          requestMethod, requestPath, elapsed, httpStatus);
      MDC.clear();
    }
  }
}
