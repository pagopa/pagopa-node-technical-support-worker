package it.gov.pagopa.nodetsworker.observability.apiconfig.cache;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * apiconfig cache API (through APIM) retrieving cache information.
 */
@RegisterRestClient(configKey = "apiconfig-cache")
@Produces(MediaType.APPLICATION_JSON)
public interface ApiConfigCacheClient {

    String HEADER_X_CACHE_ID = "X-CACHE-ID";
    String HEADER_X_CACHE_TIMESTAMP = "X-CACHE-TIMESTAMP";
    String HEADER_X_CACHE_VERSION = "X-CACHE-VERSION";

    /**
     * A null subscription key omits the header.
     */
    @GET
    @Path("/cache/id")
    @ClientHeaderParam(name = "Ocp-Apim-Subscription-Key", value = "${apiconfig-cache.subscription-key}")
    Response cacheVersion();
}
