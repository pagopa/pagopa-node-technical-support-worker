package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import jakarta.ejb.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@Startup
public class CosmosNegBizEventClient {

    @ConfigProperty(name = "bizneg.endpoint")
    private String endpoint;

    @ConfigProperty(name = "bizneg.key")
    private String key;

    private static String dbname = "db";
    private static String tablename = "negative-biz-events";

    @Inject
    Logger log;

    private CosmosPagedIterable<NegativeBizEvent> query(SqlQuerySpec query){
        log.info("executing query:"+query.getQueryText());
        CosmosClient client = new CosmosClientBuilder().endpoint(endpoint).key(key).buildClient();
        CosmosContainer container = client.getDatabase(dbname).getContainer(tablename);
        return container.queryItems(query, new CosmosQueryRequestOptions(), NegativeBizEvent.class);
    }

    public CosmosPagedIterable<NegativeBizEvent> findEvents(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = new ArrayList<SqlParameter>();
        paramList.add(new SqlParameter("@organizationFiscalCode", organizationFiscalCode));
        paramList.add(new SqlParameter("@noticeNumber", noticeNumber));
        paramList.add(new SqlParameter("@from", dateFrom.format(DateTimeFormatter.ISO_DATE)));
        paramList.add(new SqlParameter("@to", dateTo.format(DateTimeFormatter.ISO_DATE)));

        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where " +
                "c.creditor.idPA = @organizationFiscalCode " +
                "and c.debtorPosition.noticeNumber != null " +
                "and c.debtorPosition.noticeNumber = @noticeNumber " +
                "and c.paymentInfo != null " +
                "and c.paymentInfo.paymentDateTime > @from " +
                "and c.paymentInfo.paymentDateTime < @to"
        )
                .setParameters(paramList);
        return query(q);
    }


}
