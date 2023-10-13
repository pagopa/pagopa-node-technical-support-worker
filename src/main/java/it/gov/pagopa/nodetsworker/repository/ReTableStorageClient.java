//package it.gov.pagopa.nodetsworker.repository;
//
//import com.azure.data.tables.TableClient;
//import com.azure.data.tables.TableServiceClient;
//import com.azure.data.tables.TableServiceClientBuilder;
//import com.azure.data.tables.models.ListEntitiesOptions;
//import com.azure.data.tables.models.TableEntity;
//import it.gov.pagopa.nodetsworker.repository.model.EventEntity;
//import it.gov.pagopa.nodetsworker.util.Util;
//import jakarta.enterprise.context.ApplicationScoped;
//import java.time.LocalDate;
//import java.util.Arrays;
//import java.util.List;
//import java.util.stream.Collectors;
//import org.eclipse.microprofile.config.inject.ConfigProperty;
//
//// @Startup
//@ApplicationScoped
//// @UnlessBuildProfile("test")
//public class ReTableStorageClient {
//
//  @ConfigProperty(name = "re-table-storage.connection-string")
//  String connString;
//
//  @ConfigProperty(name = "re-table-storage.table-name")
//  String tableName;
//
//  private TableServiceClient tableServiceClient = null;
//
//  public TableClient getTableClient() {
//    if (tableServiceClient == null) {
//      tableServiceClient =
//          new TableServiceClientBuilder().connectionString(connString).buildClient();
//      tableServiceClient.createTableIfNotExists(tableName);
//    }
//    return tableServiceClient.getTableClient(tableName);
//  }
//
//  private List<String> propertiesToSelect =
//      Arrays.asList(
//          "serviceIdentifier",
//          "status",
//          "psp",
//          "canale",
//          "noticeNumber",
//          "paymentToken",
//          "idDominio",
//          "iuv",
//          "ccp",
//          "insertedTimestamp",
//          "tipoEvento",
//          "sottoTipoEvento",
//          "esito",
//          "businessProcess");
//
//  private String dateFilter = "PartitionKey ge '%s' and PartitionKey le '%s'";
//
//  private EventEntity tableEntityToEventEntity(TableEntity e) {
//    EventEntity ee = new EventEntity();
//        ee.setCanale(getString(e.getProperty("canale")));
//        ee.setIuv(getString(e.getProperty("iuv")));
//        ee.setCcp(getString(e.getProperty("ccp")));
//        ee.setNoticeNumber(getString(e.getProperty("noticeNumber")));
//        ee.setPaymentToken(getString(e.getProperty("paymentToken")));
//        ee.setIdDominio(getString(e.getProperty("idDominio")));
//        ee.setServiceIdentifier(getString(e.getProperty("serviceIdentifier")));
//        ee.setInsertedTimestamp(getString(e.getProperty("insertedTimestamp")));
//        ee.setPsp(getString(e.getProperty("psp")));
//        ee.setStatus(getString(e.getProperty("status")));
//        ee.setUniqueId(getString(e.getProperty("uniqueId")));
//    return ee;
//  }
//
//  public List<EventEntity> findReByCiAndNN(
//      LocalDate datefrom, LocalDate dateTo, String creditorInstitution, String noticeNumber) {
//      return runQuery(
//              String.format(dateFilter+" and idDominio eq '%s' and noticeNumber eq '%s'",
//                      Util.format(datefrom),
//                      Util.format(dateTo),
//                      creditorInstitution,
//                      noticeNumber)
//      );
//  }
//
//  public List<EventEntity> findReByCiAndIUV(
//      LocalDate datefrom, LocalDate dateTo, String creditorInstitution, String iuv) {
//      return runQuery(
//              String.format(dateFilter+" and idDominio eq '%s' and iuv eq '%s'",
//                      Util.format(datefrom),
//                      Util.format(dateTo),
//                      creditorInstitution,
//                      iuv)
//      );
//  }
//
//
//  private List<EventEntity> runQuery(String filter){
//      ListEntitiesOptions options = new ListEntitiesOptions()
//              .setFilter(filter)
//              .setSelect(propertiesToSelect);
//      return getTableClient().listEntities(options, null, null).stream()
//              .map(
//                      e -> {
//                          return tableEntityToEventEntity(e);
//                      })
//              .collect(Collectors.toList());
//  }
//
//  public List<EventEntity> findReByCiAndNNAndToken(
//      LocalDate datefrom,
//      LocalDate dateTo,
//      String creditorInstitution,
//      String noticeNumber,
//      String paymentToken) {
//    return runQuery(
//            String.format(dateFilter+" and idDominio eq '%s' and noticeNumber eq '%s' and paymentToken eq '%s'",
//            Util.format(datefrom),
//            Util.format(dateTo),
//            creditorInstitution,
//            noticeNumber,
//            paymentToken)
//    );
//  }
//
//  public List<EventEntity> findReByCiAndIUVAndCCP(
//      LocalDate datefrom, LocalDate dateTo, String creditorInstitution, String iuv, String ccp) {
//      return runQuery(
//              String.format(dateFilter+" and idDominio eq '%s' and noticeNumber eq '%s' and ccp eq '%s'",
//                      Util.format(datefrom),
//                      Util.format(dateTo),
//                      creditorInstitution,
//                      iuv,
//                      ccp)
//      );
//  }
//
//  public long findReByPartitionKey(
//          String partitionKey
//  ) {
//    ListEntitiesOptions options =
//            new ListEntitiesOptions()
//                    .setFilter(String.format("PartitionKey eq '%s'", partitionKey))
//                    .setSelect(propertiesToSelect);
//
//    return getTableClient().listEntities(options, null, null).stream().count();
//  }
//
//  private String getString(Object o) {
//    if (o == null) return null;
//    return (String) o;
//  }
//}
