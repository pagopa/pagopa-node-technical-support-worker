package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CosmosBizEventClient {

    @ConfigProperty(name = "biz.endpoint")
    private String endpoint;

    @ConfigProperty(name = "biz.key")
    private String key;

    @Inject
    Logger log;



    public CosmosPagedIterable<PositiveBizEvent> query(SqlQuerySpec query){
        log.info("executing query:"+query.getQueryText());
        CosmosClient client = new CosmosClientBuilder().endpoint(endpoint).key(key).buildClient();
        CosmosContainer container = client.getDatabase("db").getContainer("biz-events");
        return container.queryItems(query, new CosmosQueryRequestOptions(), PositiveBizEvent.class);
    }


}
