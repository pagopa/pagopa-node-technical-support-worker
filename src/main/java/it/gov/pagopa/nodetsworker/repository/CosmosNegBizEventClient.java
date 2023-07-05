package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.nodetsworker.repository.model.NegativeBizEvent;
import it.gov.pagopa.nodetsworker.util.Util;
import jakarta.ejb.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Startup
public class CosmosNegBizEventClient {

    @ConfigProperty(name = "bizneg.endpoint")
    private String endpoint;

    @ConfigProperty(name = "bizneg.key")
    private String key;

    public static String dbname = "db";
    public static String tablename = "negative-biz-events";

    private CosmosClient client;

    @Inject
    Logger log;

    private CosmosClient getClient(){
        if(client==null){
            client = new CosmosClientBuilder().endpoint(endpoint).key(key).buildClient();
        }
        return client;
    }

    private CosmosPagedIterable<NegativeBizEvent> query(SqlQuerySpec query){
        log.info("executing query:"+query.getQueryText());
        CosmosContainer container = getClient().getDatabase(dbname).getContainer(tablename);
        return container.queryItems(query, new CosmosQueryRequestOptions(), NegativeBizEvent.class);
    }

    public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndNN(String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = Arrays.asList(
                new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
                new SqlParameter("@noticeNumber", noticeNumber),
                new SqlParameter("@from", Util.toMillis(dateFrom.atStartOfDay())),
                new SqlParameter("@to", Util.toMillis(LocalDateTime.of(dateTo, LocalTime.MAX)))
        );

        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.noticeNumber = @noticeNumber" +
                " and c.timestamp > @from" +
                " and c.timestamp < @to"
        )
                .setParameters(paramList);
        return query(q);
    }
    public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndNNAndToken(String organizationFiscalCode, String noticeNumber,String paymentToken, LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = Arrays.asList(
                new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
                new SqlParameter("@noticeNumber", noticeNumber),
                new SqlParameter("@paymentToken", paymentToken),
                new SqlParameter("@from", Util.toMillis(dateFrom.atStartOfDay())),
                new SqlParameter("@to", Util.toMillis(LocalDateTime.of(dateTo, LocalTime.MAX)))
        );
        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.noticeNumber = @noticeNumber" +
                " and c.paymentInfo.paymentToken = @paymentToken" +
                " and c.timestamp > @from" +
                " and c.timestamp < @to"
        )
                .setParameters(paramList);
        return query(q);
    }

    public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndIUV(String organizationFiscalCode,String iuv, LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = Arrays.asList(
                new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
                new SqlParameter("@iuv", iuv),
                new SqlParameter("@from", Util.toMillis(dateFrom.atStartOfDay())),
                new SqlParameter("@to", Util.toMillis(LocalDateTime.of(dateTo, LocalTime.MAX)))
        );

        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.iuv = @iuv" +
                " and c.timestamp > @from" +
                " and c.timestamp < @to"
        )
                .setParameters(paramList);
        return query(q);
    }

    public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndIUVAndCCP(String organizationFiscalCode,String iuv, String ccp,LocalDate dateFrom, LocalDate dateTo){
        List<SqlParameter> paramList = Arrays.asList(
                new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
                new SqlParameter("@iuv", iuv),
                new SqlParameter("@ccp", ccp),
                new SqlParameter("@from", Util.toMillis(dateFrom.atStartOfDay())),
                new SqlParameter("@to", Util.toMillis(LocalDateTime.of(dateTo, LocalTime.MAX)))
        );

        SqlQuerySpec q = new SqlQuerySpec("SELECT * FROM c where" +
                " c.creditor.idPA = @organizationFiscalCode" +
                " and c.debtorPosition.iuv = @iuv" +
                " and c.paymentInfo.paymentToken = @ccp" +
                " and c.timestamp > @from" +
                " and c.timestamp < @to"
        )
                .setParameters(paramList);
        return query(q);
    }

    public CosmosPagedIterable<NegativeBizEvent> findEventsByCiAndIUVAndToken(String organizationFiscalCode,String iuv, String paymentToken,LocalDate dateFrom, LocalDate dateTo){
        return findEventsByCiAndIUVAndCCP(organizationFiscalCode,iuv,paymentToken,dateFrom,dateTo);
    }

}
