package it.gov.pagopa.nodetsworker.resources.filter;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.jboss.resteasy.reactive.server.core.Deployment;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;
import org.jboss.resteasy.reactive.server.jaxrs.UriInfoImpl;
import org.jboss.resteasy.reactive.server.spi.ServerHttpRequest;
import java.net.URI;
import java.util.List;

class ReRequestLoggingTest {
  @Test
  void realUriInfoDecodedQuestionMarkNeverLeaksRejectedQuery() throws Exception {
    assertDecodedPathNotLogged("%3F", "?");
  }

  @Test
  void realUriInfoDecodedHashNeverLeaksRejectedQuery() throws Exception {
    assertDecodedPathNotLogged("%23", "#");
  }

  private void assertDecodedPathNotLogged(String encodedDelimiter, String decodedDelimiter) throws Exception {
    URI target = URI.create("https://synthetic.invalid/api/attempts/synthetic"
        + encodedDelimiter + "secret/re-events?token=synthetic-secret");
    var context = mock(ResteasyReactiveRequestContext.class);
    var deployment = mock(Deployment.class);
    var serverRequest = mock(ServerHttpRequest.class);
    when(context.getPath()).thenReturn(target.getRawPath());
    when(context.getDeployment()).thenReturn(deployment);
    when(deployment.getPrefix()).thenReturn("");
    when(context.serverRequest()).thenReturn(serverRequest);
    when(serverRequest.queryParamNames()).thenReturn(List.of("token"));
    when(serverRequest.getAllQueryParams("token")).thenReturn(List.of("synthetic-secret"));
    var uri = new UriInfoImpl(context);
    assertEquals("/api/attempts/synthetic" + decodedDelimiter + "secret/re-events", uri.getPath());
    assertEquals("synthetic-secret", uri.getQueryParameters().getFirst("token"));
    var request = mock(ContainerRequestContext.class);
    when(request.getUriInfo()).thenReturn(uri);
    var filter = new RequestFilter();
    filter.log = mock(Logger.class);
    filter.filter(request);
    verifyNoInteractions(filter.log);
    verify(request, never()).setProperty(eq("requestStartTime"), any());
  }

  @Test
  void diagnosticRequestsNeverLogBodyPathOrRejectedQueryTokens() throws Exception {
    var filter = new RequestFilter();
    filter.log = mock(Logger.class);
    var request = mock(ContainerRequestContext.class);
    var uri = mock(UriInfo.class);
    when(request.getUriInfo()).thenReturn(uri);
    for (String path : List.of("/api/attempts/synthetic-id/re-events",
        "/api/attempts/synthetic-id/re-events;v=1",
        "/api/attempts/synthetic-id/re%2devents%3bv=1",
        "/api/attempts/synthetic%3Fsecret/re%2Devents",
        "/api/attempts/synthetic%23secret/re%2Devents",
        "/api/attempts/synthetic-id/re-events%253bv=1")) {
      when(uri.getPath()).thenReturn(path);
      filter.filter(request);
    }
    verifyNoInteractions(filter.log);
    verify(uri, never()).getQueryParameters();
    verify(request, never()).setProperty(eq("requestStartTime"), any());
  }
}
