//package it.gov.pagopa.nodetsworker.repository;
//
//import com.azure.cosmos.CosmosClient;
//import com.azure.cosmos.CosmosClientBuilder;
//import com.azure.cosmos.CosmosContainer;
//import com.azure.cosmos.models.CosmosQueryRequestOptions;
//import com.azure.cosmos.models.SqlParameter;
//import com.azure.cosmos.models.SqlQuerySpec;
//import com.azure.cosmos.util.CosmosPagedIterable;
//import io.quarkus.runtime.Startup;
//import it.gov.pagopa.nodetsworker.repository.model.EventEntity;
//import it.gov.pagopa.nodetsworker.util.Util;
//import jakarta.enterprise.context.ApplicationScoped;
//import jakarta.inject.Inject;
//import org.eclipse.microprofile.config.inject.ConfigProperty;
//import org.jboss.logging.Logger;
//
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.LocalTime;
//import java.util.Arrays;
//import java.util.List;
//
//@ApplicationScoped
//@Startup
//public class CosmosReEventClient {
//
//  @ConfigProperty(name = "cosmos-re.endpoint")
//  private String endpoint;
//
//  @ConfigProperty(name = "cosmos-re.key")
//  private String key;
//
//  public static String dbname = "nodo_re";
//  public static String tablename = "events";
//
//  private CosmosClient client;
//
//  @Inject Logger log;
//
//  private CosmosClient getClient() {
//    if (client == null) {
//      client = new CosmosClientBuilder().endpoint(endpoint).key(key).buildClient();
//    }
//    return client;
//  }
//
//  private CosmosPagedIterable<EventEntity> query(SqlQuerySpec query) {
//    log.info("executing query:" + query.getQueryText());
//    CosmosContainer container = getClient().getDatabase(dbname).getContainer(tablename);
//    return container.queryItems(query, new CosmosQueryRequestOptions(), EventEntity.class);
//  }
//
//
//  public CosmosPagedIterable<EventEntity> findReByCiAndNNAndToken(
//      String organizationFiscalCode,
//      String noticeNumber,
//      String paymentToken,
//      LocalDate dateFrom,
//      LocalDate dateTo) {
//    List<SqlParameter> paramList =
//        Arrays.asList(
//            new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
//            new SqlParameter("@noticeNumber", noticeNumber),
//            new SqlParameter("@paymentToken", paymentToken),
//            new SqlParameter("@from", Util.toMillis(dateFrom.atStartOfDay())),
//            new SqlParameter("@to", Util.toMillis(LocalDateTime.of(dateTo, LocalTime.MAX))));
//    SqlQuerySpec q =
//        new SqlQuerySpec(
//                "SELECT * FROM c where"
//                    + " c.creditor.idPA = @organizationFiscalCode"
//                    + " and c.debtorPosition.noticeNumber = @noticeNumber"
//                    + " and c.paymentInfo.paymentToken = @paymentToken"
//                    + " and c.timestamp > @from"
//                    + " and c.timestamp < @to")
//            .setParameters(paramList);
//    return query(q);
//  }
//
//  public CosmosPagedIterable<EventEntity> findReByCiAndIUVAndCCP(
//      String organizationFiscalCode, String iuv, String ccp, LocalDate dateFrom, LocalDate dateTo) {
//    List<SqlParameter> paramList =
//        Arrays.asList(
//            new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
//            new SqlParameter("@iuv", iuv),
//            new SqlParameter("@ccp", ccp),
//            new SqlParameter("@from", Util.toMillis(dateFrom.atStartOfDay())),
//            new SqlParameter("@to", Util.toMillis(LocalDateTime.of(dateTo, LocalTime.MAX))));
//    SqlQuerySpec q =
//        new SqlQuerySpec(
//                "SELECT * FROM c where"
//                    + " c.creditor.idPA = @organizationFiscalCode"
//                    + " and c.debtorPosition.iuv = @iuv"
//                    + " and c.paymentInfo.paymentToken = @ccp"
//                    + " and c.timestamp > @from"
//                    + " and c.timestamp < @to")
//            .setParameters(paramList);
//    return query(q);
//  }
//
//  public CosmosPagedIterable<EventEntity> findReByCiAndNN(
//      String organizationFiscalCode, String noticeNumber, LocalDate dateFrom, LocalDate dateTo) {
//    List<SqlParameter> paramList =
//        Arrays.asList(
//            new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
//            new SqlParameter("@noticeNumber", noticeNumber),
//            new SqlParameter("@from", Util.toMillis(dateFrom.atStartOfDay())),
//            new SqlParameter("@to", Util.toMillis(LocalDateTime.of(dateTo, LocalTime.MAX))));
//
//    SqlQuerySpec q =
//        new SqlQuerySpec(
//                "SELECT * FROM c where"
//                    + " c.creditor.idPA = @organizationFiscalCode"
//                    + " and c.debtorPosition.noticeNumber = @noticeNumber"
//                    + " and c.timestamp > @from"
//                    + " and c.timestamp < @to")
//            .setParameters(paramList);
//    return query(q);
//  }
//
//  public CosmosPagedIterable<EventEntity> findReByCiAndIUV(
//      String organizationFiscalCode, String iuv, LocalDate dateFrom, LocalDate dateTo) {
//    List<SqlParameter> paramList =
//        Arrays.asList(
//            new SqlParameter("@organizationFiscalCode", organizationFiscalCode),
//            new SqlParameter("@iuv", iuv),
//            new SqlParameter("@from", Util.toMillis(dateFrom.atStartOfDay())),
//            new SqlParameter("@to", Util.toMillis(LocalDateTime.of(dateTo, LocalTime.MAX))));
//
//    SqlQuerySpec q =
//        new SqlQuerySpec(
//                "SELECT * FROM c where"
//                    + " c.creditor.idPA = @organizationFiscalCode"
//                    + " and c.debtorPosition.iuv = @iuv"
//                    + " and c.timestamp > @from"
//                    + " and c.timestamp < @to")
//            .setParameters(paramList);
//    return query(q);
//  }
//
//}
