package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.Optional;

@ApplicationScoped
class CosmosClientsConfig {

    @ConfigProperty(name = "biz.endpoint")
    String bizendpoint;
    @ConfigProperty(name = "biz.key")
    String bizkey;

    @ConfigProperty(name = "verifyko.endpoint")
    String verifykoendpoint;
    @ConfigProperty(name = "verifyko.key")
    String verifykokey;

    @ConfigProperty(name = "bizneg.endpoint")
    String biznegendpoint;
    @ConfigProperty(name = "bizneg.key")
    String biznegkey;

    /**
     * Preferred region for Cosmos DB client.
     * If set, the client will try this region first (read/write) and fallback to others if needed.
     * For prod environment the property is expected to be set to replica region, to avoid performance issues on the primary region.
     */
    @ConfigProperty(name = "cosmos.preferred.region")
    Optional<String> preferredRegion;

    @Produces
    @Named("biz")
    @ApplicationScoped
    CosmosClient bizClient() {
        return baseBuilder(bizendpoint, bizkey).buildClient();
    }

    @Produces
    @Named("bizneg")
    @ApplicationScoped
    CosmosClient negbizClient() {
        return baseBuilder(biznegendpoint, biznegkey).buildClient();
    }

    @Produces
    @Named("verifyKo")
    @ApplicationScoped
    CosmosClient verifyKoClient() {
        return baseBuilder(verifykoendpoint, verifykokey).buildClient();
    }

    private CosmosClientBuilder baseBuilder(String endpoint, String key) {
        CosmosClientBuilder b = new CosmosClientBuilder()
                .endpoint(endpoint)
                .key(key);

        preferredRegion
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .ifPresent(r -> b.preferredRegions(Collections.singletonList(r)));

        return b;
    }
}