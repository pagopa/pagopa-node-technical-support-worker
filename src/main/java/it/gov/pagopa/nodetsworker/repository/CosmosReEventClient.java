package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import io.quarkus.runtime.Startup;
import it.gov.pagopa.nodetsworker.repository.model.Count;
import it.gov.pagopa.nodetsworker.repository.model.EventEntity;
import it.gov.pagopa.nodetsworker.util.Util;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Startup
public class CosmosReEventClient {

  @ConfigProperty(name = "re.endpoint")
  private String endpoint;

  @ConfigProperty(name = "re.key")
  private String key;

  public static String dbname = "nodo_re";
  public static String tablename = "events";

  private CosmosClient client;

  @Inject Logger log;

  private String dateFilter = " and c.insertedTimestamp > @from and c.insertedTimestamp < @to";

  private CosmosClient getClient() {
    if (client == null) {
      client = new CosmosClientBuilder().endpoint(endpoint).key(key).buildClient();
    }
    return client;
  }

  private CosmosPagedIterable<EventEntity> query(SqlQuerySpec query) {
    log.info("executing query:" + query.getQueryText());
    CosmosContainer container = getClient().getDatabase(dbname).getContainer(tablename);
    return container.queryItems(query, new CosmosQueryRequestOptions(), EventEntity.class);
  }

  private CosmosPagedIterable<Count> queryCount(SqlQuerySpec query){
    log.info("executing query:"+query.getQueryText());
    CosmosClient client = new CosmosClientBuilder().endpoint(endpoint).key(key).buildClient();
    CosmosContainer container = getClient().getDatabase(dbname).getContainer(tablename);
    return container.queryItems(query, new CosmosQueryRequestOptions(), Count.class);
  }

  public CosmosPagedIterable<EventEntity> findReByCiAndNNAndToken(
      String organizationFiscalCode,
      String noticeNumber,
      Optional<String> paymentToken,
      LocalDate dateFrom,
      LocalDate dateTo) {
    List<SqlParameter> paramList =
        Arrays.asList(
            new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
            new SqlParameter("@noticeNumber", noticeNumber),
            new SqlParameter("@from", Util.format(dateFrom)),
            new SqlParameter("@to", Util.format(dateTo.plusDays(1)))
        );
    paymentToken.ifPresent(pt->paramList.add(new SqlParameter("@paymentToken", paymentToken)));
    SqlQuerySpec q =
        new SqlQuerySpec(
                "SELECT * FROM c where"
                    + " c.idDominio = @organizationFiscalCode"
                    + " and c.noticeNumber = @noticeNumber"
                    + paymentToken.map(pt->" and c.paymentToken = @paymentToken").orElse("")
                    + dateFilter)
            .setParameters(paramList);
    return query(q);
  }

  public CosmosPagedIterable<EventEntity> findReByCiAndIUVAndCCP(
      String organizationFiscalCode, String iuv,
      Optional<String> ccp, LocalDate dateFrom, LocalDate dateTo) {
    List<SqlParameter> paramList =
        Arrays.asList(
            new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
            new SqlParameter("@iuv", iuv),
            new SqlParameter("@from", Util.format(dateFrom)),
            new SqlParameter("@to", Util.format(dateTo.plusDays(1)))
        );
    ccp.ifPresent(cp->paramList.add(new SqlParameter("@ccp", cp)));
    SqlQuerySpec q =
        new SqlQuerySpec(
                "SELECT * FROM c where"
                    + " c.idDominio = @organizationFiscalCode"
                    + " and c.iuv = @iuv"
                    + ccp.map(pt->" and c.ccp = @ccp").orElse("")
                    + dateFilter)
            .setParameters(paramList);
    return query(q);
  }

  public CosmosPagedIterable<Count> findReByPartitionKey(String pk){
    List<SqlParameter> paramList = Arrays.asList(new SqlParameter("@pk", pk));
    SqlQuerySpec q = new SqlQuerySpec("SELECT count(1) as count FROM c where inserted_date = @pk").setParameters(paramList);
    return queryCount(q);
  }
}
