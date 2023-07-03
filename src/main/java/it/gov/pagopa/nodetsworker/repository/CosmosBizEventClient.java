package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.nodetsworker.repository.model.Count;
import it.gov.pagopa.nodetsworker.repository.model.NegativeBizEvent;
import it.gov.pagopa.nodetsworker.repository.model.PositiveBizEvent;
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
public class CosmosBizEventClient {

    @ConfigProperty(name = "biz.endpoint")
    private String endpoint;

    @ConfigProperty(name = "biz.key")
    private String key;

    private static String dbname = "db";
    private static String tablename = "biz-events";

    @Inject
    Logger log;

    private CosmosPagedIterable<PositiveBizEvent> query(SqlQuerySpec query){
        log.info("executing query:"+query.getQueryText());
        CosmosClient client = new CosmosClientBuilder().endpoint(endpoint).key(key).buildClient();
        CosmosContainer container = client.getDatabase(dbname).getContainer(tablename);
        return container.queryItems(query, new CosmosQueryRequestOptions(), PositiveBizEvent.class);
    }
    private CosmosPagedIterable<Count> queryCount(SqlQuerySpec query){
        log.info("executing query:"+query.getQueryText());
        CosmosClient client = new CosmosClientBuilder().endpoint(endpoint).key(key).buildClient();
        CosmosContainer container = client.getDatabase(dbname).getContainer(tablename);
        return container.queryItems(query, new CosmosQueryRequestOptions(), Count.class);
    }

    public CosmosPagedIterable<PositiveBizEvent> findEventsByCiAndNNAndToken(String organizationFiscalCode, String noticeNumber,String paymentToken, LocalDate dateFrom, LocalDate dateTo){
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

    public CosmosPagedIterable<PositiveBizEvent> findEventsByCiAndIUVAndCCP(String organizationFiscalCode, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo){
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

    public CosmosPagedIterable<PositiveBizEvent> findEvents(String organizationFiscalCode, String noticeNumber, Optional<String> paymentToken, Optional<String> iuv, LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = new ArrayList<SqlParameter>();
        paramList.add(new SqlParameter("@organizationFiscalCode", organizationFiscalCode));
        paramList.add(new SqlParameter("@noticeNumber", noticeNumber));
        paramList.add(new SqlParameter("@from", dateFrom.format(DateTimeFormatter.ISO_DATE)));
        paramList.add(new SqlParameter("@to", dateTo.format(DateTimeFormatter.ISO_DATE)));
        paymentToken.ifPresent(pt->{
            paramList.add(new SqlParameter("@paymentToken", pt));
        });
        iuv.ifPresent(pt->{
            paramList.add(new SqlParameter("@iuv", pt));
        });

        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.noticeNumber = @noticeNumber" +
                " and c.paymentInfo.paymentDateTime > @from" +
                " and c.paymentInfo.paymentDateTime < @to" +
                (paymentToken.isPresent()? " and c.paymentInfo.paymentToken = @paymentToken" : "") +
                (paymentToken.isPresent()? " and c.paymentInfo.iuv = @iuv" : "")
        )
        .setParameters(paramList);
        return query(q);
    }

    public CosmosPagedIterable<Count> countEventsByCiAndNN(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = new ArrayList<SqlParameter>();
        paramList.add(new SqlParameter("@organizationFiscalCode", organizationFiscalCode));
        paramList.add(new SqlParameter("@noticeNumber", noticeNumber));
        paramList.add(new SqlParameter("@from", dateFrom.format(DateTimeFormatter.ISO_DATE)));
        paramList.add(new SqlParameter("@to", dateTo.format(DateTimeFormatter.ISO_DATE)));


        SqlQuerySpec q = new SqlQuerySpec("SELECT count(1) as count FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.noticeNumber = @noticeNumber" +
                " and c.paymentInfo.paymentDateTime > @from" +
                " and c.paymentInfo.paymentDateTime < @to"
        )
                .setParameters(paramList);
        return queryCount(q);
    }

    public CosmosPagedIterable<Count> countEventsByCiAndIUV(String organizationFiscalCode, String iuv, LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = new ArrayList<SqlParameter>();
        paramList.add(new SqlParameter("@organizationFiscalCode", organizationFiscalCode));
        paramList.add(new SqlParameter("@iuv", iuv));
        paramList.add(new SqlParameter("@from", dateFrom.format(DateTimeFormatter.ISO_DATE)));
        paramList.add(new SqlParameter("@to", dateTo.format(DateTimeFormatter.ISO_DATE)));


        SqlQuerySpec q = new SqlQuerySpec("SELECT count(1) as count FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.iuv = @iuv" +
                " and c.paymentInfo.paymentDateTime > @from" +
                " and c.paymentInfo.paymentDateTime < @to"
        )
                .setParameters(paramList);
        return queryCount(q);
    }

}
