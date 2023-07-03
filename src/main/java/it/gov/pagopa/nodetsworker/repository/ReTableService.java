package it.gov.pagopa.nodetsworker.repository;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import it.gov.pagopa.nodetsworker.repository.model.EventEntity;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// @Startup
@ApplicationScoped
// @UnlessBuildProfile("test")
public class ReTableService {

  @ConfigProperty(name = "re-table-storage.connection-string")
  String connString;

  @ConfigProperty(name = "re-table-storage.table-name")
  String tableName;

  private TableServiceClient tableServiceClient = null;

  public TableClient getTableClient(){
    if(tableServiceClient==null){
      tableServiceClient = new TableServiceClientBuilder().connectionString(connString).buildClient();
    }
    return tableServiceClient.getTableClient(tableName);
  }

  private List<String> propertiesToSelect = Arrays.asList(
          "serviceIdentifier",
          "status",
          "psp",
          "canale",
          "noticeNumber",
          "paymentToken",
          "idDominio",
          "iuv",
          "ccp",
          "insertedTimestamp"
  );


  private EventEntity tableEntityToEventEntity(TableEntity e){
    return EventEntity.builder()
            .canale(getString(e.getProperty("canale")))
            .iuv(getString(e.getProperty("iuv")))
            .ccp(getString(e.getProperty("ccp")))
            .noticeNumber(getString(e.getProperty("noticeNumber")))
            .paymentToken(getString(e.getProperty("paymentToken")))
            .idDominio(getString(e.getProperty("idDominio")))
            .serviceIdentifier(getString(e.getProperty("serviceIdentifier")))
            .insertedTimestamp(getString(e.getProperty("insertedTimestamp")))
            .psp(getString(e.getProperty("psp")))
            .status(getString(e.getProperty("status")))
            .uniqueId(getString(e.getProperty("uniqueId")))
            .build();
  }

  public List<EventEntity> findReByCiAndNN(LocalDate datefrom, LocalDate dateTo, String creditorInstitution, String noticeNumber){
    ListEntitiesOptions options = new ListEntitiesOptions()
            .setFilter(String.format("PartitionKey gt '%s' and PartitionKey lt '%s' and idDominio eq '%s' and noticeNumber eq '%s' and esito eq 'CAMBIO_STATO'",
                    datefrom,dateTo, creditorInstitution, noticeNumber))
            .setSelect(propertiesToSelect);
    return getTableClient().listEntities(options, null, null).stream().map(e->{return tableEntityToEventEntity(e);}).collect(Collectors.toList());
  }
  public List<EventEntity> findReByCiAndIUV(LocalDate datefrom, LocalDate dateTo, String creditorInstitution, String iuv){
    ListEntitiesOptions options = new ListEntitiesOptions()
            .setFilter(String.format("PartitionKey gt '%s' and PartitionKey lt '%s' and idDominio eq '%s' and iuv eq '%s' and esito eq 'CAMBIO_STATO'",
                    datefrom,dateTo, creditorInstitution, iuv))
            .setSelect(propertiesToSelect);
    return getTableClient().listEntities(options, null, null).stream().map(e->{return tableEntityToEventEntity(e);}).collect(Collectors.toList());
  }

  public List<EventEntity> findReByCiAndNNAndToken(LocalDate datefrom, LocalDate dateTo, String creditorInstitution, String noticeNumber, String paymentToken){
    ListEntitiesOptions options = new ListEntitiesOptions()
            .setFilter(String.format("PartitionKey gt '%s' and PartitionKey lt '%s' and idDominio eq '%s' and noticeNumber eq '%s' and paymentToken eq '%s' and esito eq 'CAMBIO_STATO'",
                    datefrom,dateTo, creditorInstitution, noticeNumber,paymentToken))
            .setSelect(propertiesToSelect);
    return getTableClient().listEntities(options, null, null).stream().map(e->{return tableEntityToEventEntity(e);}).collect(Collectors.toList());
  }

  public List<EventEntity> findReByCiAndIUVAndCCP(LocalDate datefrom, LocalDate dateTo, String creditorInstitution, String iuv,String ccp){
    ListEntitiesOptions options = new ListEntitiesOptions()
            .setFilter(String.format("PartitionKey gt '%s' and PartitionKey lt '%s' and idDominio eq '%s' and iuv eq '%s' and ccp eq '%s' and esito eq 'CAMBIO_STATO'",
                    datefrom,dateTo, creditorInstitution, iuv,ccp))
            .setSelect(propertiesToSelect);
    return getTableClient().listEntities(options, null, null).stream().map(e->{return tableEntityToEventEntity(e);}).collect(Collectors.toList());
  }

  private String getString(Object o){
    if(o==null) return null;
    return (String)o;
  }

}
