package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import io.quarkus.runtime.Startup;
import it.gov.pagopa.nodetsworker.repository.models.VerifyKOEvent;
import it.gov.pagopa.nodetsworker.util.Util;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
@Startup
public class CosmosVerifyKOEventClient {

  public static final String DBNAME = "nodo_verifyko";
  public static final String TABLENAME = "events";

  @Inject
  @Named("verifyKo")
  private CosmosClient client;

  @Inject Logger log;

  private static final String DATEFILTER = " and c.faultBean.dateTime > @from and c.faultBean.dateTime < @to";

  private CosmosPagedIterable<VerifyKOEvent> query(SqlQuerySpec query) {
    log.info("executing query:" + query.getQueryText());
    CosmosContainer container = client.getDatabase(DBNAME).getContainer(TABLENAME);
    return container.queryItems(query, new CosmosQueryRequestOptions(), VerifyKOEvent.class);
  }

  public CosmosPagedIterable<VerifyKOEvent> findEventsByCiAndNN(
      String organizationFiscalCode,
      String noticeNumber,
      LocalDate dateFrom,
      LocalDate dateTo) {
    List<SqlParameter> paramList = new ArrayList<>();
    paramList.addAll(Arrays.asList(
            new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
            new SqlParameter("@noticeNumber", noticeNumber),
            new SqlParameter("@from", Util.format(dateFrom)),
            new SqlParameter("@to", Util.format(dateTo.plusDays(1)))
        ));
    SqlQuerySpec q =
        new SqlQuerySpec(
                "SELECT * FROM c where"
                    + " c.creditor.idPA = @organizationFiscalCode"
                    + " and c.debtorPosition.noticeNumber = @noticeNumber"
                    + DATEFILTER
        )
            .setParameters(paramList);
    return query(q);
  }

}
