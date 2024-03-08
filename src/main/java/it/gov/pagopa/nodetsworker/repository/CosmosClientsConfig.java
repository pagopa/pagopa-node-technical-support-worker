package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@UnlessBuildProfile("test")
class CosmosClientsConfig {


    @ConfigProperty(name = "biz.endpoint")
    private String bizendpoint;
    @ConfigProperty(name = "biz.key")
    private String bizkey;
    @ConfigProperty(name = "verifyko.endpoint")
    private String verifykoendpoint;
    @ConfigProperty(name = "verifyko.key")
    private String verifykokey;
    @ConfigProperty(name = "bizneg.endpoint")
    private String biznegendpoint;
    @ConfigProperty(name = "bizneg.key")
    private String biznegkey;


    @Produces
    @Named("biz")
    @ApplicationScoped
    CosmosClient bizClient() {
        return new CosmosClientBuilder().endpoint(bizendpoint).key(bizkey).buildClient();
    }

    @Produces
    @Named("bizneg")
    @ApplicationScoped
    CosmosClient negbizClient() {
        return new CosmosClientBuilder().endpoint(biznegendpoint).key(biznegkey).buildClient();
    }

    @Produces
    @Named("verifyKo")
    @ApplicationScoped
    CosmosClient verifyKoClient() {
        return new CosmosClientBuilder().endpoint(verifykoendpoint).key(verifykokey).buildClient();
    }
}
