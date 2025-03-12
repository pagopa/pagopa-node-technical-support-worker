package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import io.quarkus.runtime.Startup;
import it.gov.pagopa.nodetsworker.repository.models.NegativeBizEvent;
import it.gov.pagopa.nodetsworker.util.Util;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Startup
public class CosmosNegBizEventClient {

  public static final String DBNAME = "db";
  public static final String TABLENAME = "negative-biz-events";

  @Inject
  @Named("bizneg")
  private CosmosClient client;

  @Inject Logger log;

  private static final String DATEFILTER = " and c.paymentInfo.paymentDateTime > @from and c.paymentInfo.paymentDateTime < @to";

  private CosmosPagedIterable<NegativeBizEvent> query(SqlQuerySpec query) {
    log.info("executing query:" + query.getQueryText());
    CosmosContainer container = client.getDatabase(DBNAME).getContainer(TABLENAME);
    return container.queryItems(query, new CosmosQueryRequestOptions(), NegativeBizEvent.class);
  }

  public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndNNAndToken(
      String organizationFiscalCode,
      String noticeNumber,
      Optional<String> paymentToken,
      LocalDate dateFrom,
      LocalDate dateTo) {
    List<SqlParameter> paramList = new ArrayList<>();
    paramList.addAll(Arrays.asList(
            new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
            new SqlParameter("@noticeNumber", noticeNumber),
            new SqlParameter("@from", Util.format(dateFrom)),
            new SqlParameter("@to", Util.format(dateTo.plusDays(1)))
        ));
    paymentToken.ifPresent(pt->paramList.add( new SqlParameter("@paymentToken", pt)));
    SqlQuerySpec q =
        new SqlQuerySpec(
                "SELECT * FROM c where"
                    + " c.creditor.idPA = @organizationFiscalCode"
                    + " and c.debtorPosition.noticeNumber = @noticeNumber"
                    + (paymentToken.isPresent()?" and c.paymentInfo.paymentToken = @paymentToken":"")
                    + DATEFILTER)
            .setParameters(paramList);
    return query(q);
  }

  public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndIUVAndCCP(
      String organizationFiscalCode, String iuv, Optional<String> ccp, LocalDate dateFrom, LocalDate dateTo) {
    List<SqlParameter> paramList = new ArrayList<>();
    paramList.addAll(Arrays.asList(
            new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
            new SqlParameter("@iuv", iuv),
            new SqlParameter("@from", Util.format(dateFrom)),
            new SqlParameter("@to", Util.format(dateTo.plusDays(1)))
        ));
    ccp.ifPresent(cp->paramList.add( new SqlParameter("@ccp", cp)));
    SqlQuerySpec q =
        new SqlQuerySpec(
                "SELECT * FROM c where"
                    + " c.creditor.idPA = @organizationFiscalCode"
                    + " and c.debtorPosition.iuv = @iuv"
                    + (ccp.isPresent()?" and c.paymentInfo.paymentToken = @ccp":"")
                    + DATEFILTER)
            .setParameters(paramList);
    return query(q);
  }

  public CosmosPagedIterable<NegativeBizEvent> getNegativeBizEventById(String id) {
    SqlParameter param = new SqlParameter("@bizEventId", id);
    SqlQuerySpec q =
            new SqlQuerySpec(
                    "SELECT * FROM c where"
                            + " c.id = @bizEventId"
                            + " offset 0 limit 1")
                    .setParameters(List.of(param));
    return query(q);
  }
}
