package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.nodetsworker.repository.model.NegativeBizEvent;
import jakarta.ejb.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndNN(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = new ArrayList<SqlParameter>();
        paramList.add(new SqlParameter("@organizationFiscalCode", organizationFiscalCode));
        paramList.add(new SqlParameter("@noticeNumber", noticeNumber));
        paramList.add(new SqlParameter("@from", dateFrom.format(DateTimeFormatter.ISO_DATE)));
        paramList.add(new SqlParameter("@to", dateTo.format(DateTimeFormatter.ISO_DATE)));
        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.noticeNumber = @noticeNumber" +
                " and c.paymentInfo.paymentDateTime > @from" +
                " and c.paymentInfo.paymentDateTime < @to"
        )
                .setParameters(paramList);
        return query(q);
    }
    public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndNNAndToken(String organizationFiscalCode, String noticeNumber,String paymentToken, LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = new ArrayList<SqlParameter>();
        paramList.add(new SqlParameter("@organizationFiscalCode", organizationFiscalCode));
        paramList.add(new SqlParameter("@noticeNumber", noticeNumber));
        paramList.add(new SqlParameter("@from", dateFrom.format(DateTimeFormatter.ISO_DATE)));
        paramList.add(new SqlParameter("@to", dateTo.format(DateTimeFormatter.ISO_DATE)));
        paramList.add(new SqlParameter("@paymentToken", paymentToken));
        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.noticeNumber = @noticeNumber" +
                " and c.paymentInfo.paymentToken = @paymentToken" +
                " and c.paymentInfo.paymentDateTime > @from" +
                " and c.paymentInfo.paymentDateTime < @to"
        )
                .setParameters(paramList);
        return query(q);
    }

    public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndIUV(String organizationFiscalCode,String iuv, LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = new ArrayList<SqlParameter>();
        paramList.add(new SqlParameter("@organizationFiscalCode", organizationFiscalCode));
        paramList.add(new SqlParameter("@iuv", iuv));
        paramList.add(new SqlParameter("@from", dateFrom.format(DateTimeFormatter.ISO_DATE)));
        paramList.add(new SqlParameter("@to", dateTo.format(DateTimeFormatter.ISO_DATE)));

        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.iuv = @iuv" +
                " and c.paymentInfo.paymentDateTime > @from" +
                " and c.paymentInfo.paymentDateTime < @to"
        )
                .setParameters(paramList);
        return query(q);
    }

    public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndIUVAndCCP(String organizationFiscalCode,String iuv, String ccp,LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = new ArrayList<SqlParameter>();
        paramList.add(new SqlParameter("@organizationFiscalCode", organizationFiscalCode));
        paramList.add(new SqlParameter("@iuv", iuv));
        paramList.add(new SqlParameter("@ccp", ccp));
        paramList.add(new SqlParameter("@from", dateFrom.format(DateTimeFormatter.ISO_DATE)));
        paramList.add(new SqlParameter("@to", dateTo.format(DateTimeFormatter.ISO_DATE)));

        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.iuv = @iuv" +
                " and c.paymentInfo.paymentToken = @ccp" +
                " and c.paymentInfo.paymentDateTime > @from" +
                " and c.paymentInfo.paymentDateTime < @to"
        )
                .setParameters(paramList);
        return query(q);
    }

    public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndIUVAndToken(String organizationFiscalCode,String iuv, String paymentToken,LocalDate dateFrom, LocalDate dateTo){
        return findEventsByCiAndIUVAndCCP(organizationFiscalCode,iuv,paymentToken,dateFrom,dateTo);
    }

}
