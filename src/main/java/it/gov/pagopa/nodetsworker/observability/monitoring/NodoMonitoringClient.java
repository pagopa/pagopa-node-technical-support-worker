package it.gov.pagopa.nodetsworker.observability.monitoring;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** Nodo monitoring API exposed through APIM; {@code X-Ndp-Target} selects the node instance. */
@RegisterRestClient(configKey = "nodo-monitoring")
@Path("/monitoring/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface NodoMonitoringClient {

  @GET
  @Path("/cache/version")
  CompletionStage<CacheVersion> cacheVersion(
      @HeaderParam("X-Ndp-Target") String target,
      @HeaderParam("Ocp-Apim-Subscription-Key") String subscriptionKey);
}
