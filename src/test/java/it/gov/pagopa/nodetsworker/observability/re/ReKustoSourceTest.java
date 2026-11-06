package it.gov.pagopa.nodetsworker.observability.re;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.kusto.data.Client;
import com.microsoft.azure.kusto.data.ClientFactory;
import com.microsoft.azure.kusto.data.ClientRequestProperties;
import com.microsoft.azure.kusto.data.KustoOperationResult;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;
import com.microsoft.azure.kusto.data.exceptions.DataClientException;
import com.microsoft.azure.kusto.data.exceptions.DataServiceException;
import com.microsoft.azure.kusto.data.exceptions.ThrottleException;
import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.http.impl.client.CloseableHttpClient;

class ReKustoSourceTest {
  private final ReKustoSource source = new ReKustoSource();
  private final Client client = mock(Client.class);
  private final ReConfig config = new ReConfig();
  private final Instant from = Instant.parse("2026-10-23T10:00:00Z");

  @BeforeEach
  void setup() {
    config.enabled = true;
    config.schemaVerified = config.retentionVerified = config.timezoneVerified = true;
    config.orderingVerified = config.watermarkVerified = config.personalDataApproved = true;
    config.endpoint = Optional.of("https://synthetic.kusto.windows.net");
    config.clientId = Optional.of("synthetic-client");
    config.clientSecret = Optional.of("synthetic-secret");
    config.tenantId = Optional.of("synthetic-tenant");
    config.database = Optional.of("synthetic");
    config.table = Optional.of("ReEvent");
    config.rowIdColumn = Optional.of("technicalRowId");
    config.retentionDays = 2;
    source.config = config;
    source.client = client;
  }

  @AfterEach
  void close() {
    source.close();
  }

  @Test
  void missingApplicationCredentialsFailClosedBeforeQuerying() {
    List<java.util.function.Consumer<Optional<String>>> credentials = List.of(
        value -> config.clientId = value,
        value -> config.clientSecret = value,
        value -> config.tenantId = value);
    for (var credential : credentials) {
      for (Optional<String> invalid : List.of(Optional.<String>empty(), Optional.of(" \t"))) {
        credential.accept(invalid);
        var failure = assertThrows(ObservabilityException.class, () -> source.read(query()));
        assertEquals(503, failure.getStatus());
        assertNull(failure.getCause());
        credential.accept(Optional.of("synthetic-credential"));
      }
    }
    verifyNoInteractions(client);
  }

  @Test
  void createsLazyClientWithApplicationCredentialsAndReusesIt() throws Exception {
    source.client = null;
    var connection = ConnectionStringBuilder.createWithAadApplicationCredentials(
        "https://synthetic.kusto.windows.net", "synthetic-client", "synthetic-secret", "synthetic-tenant");
    try (var builders = mockStatic(ConnectionStringBuilder.class);
        var clients = mockStatic(ClientFactory.class)) {
      builders.when(() -> ConnectionStringBuilder.createWithAadApplicationCredentials(
          "https://synthetic.kusto.windows.net", "synthetic-client", "synthetic-secret", "synthetic-tenant"))
          .thenReturn(connection);
      clients.when(() -> ClientFactory.createClient(eq(connection), any(CloseableHttpClient.class)))
          .thenAnswer(call -> {
            call.getArgument(1, CloseableHttpClient.class).close();
            return client;
          });
      var createClient = ReKustoSource.class.getDeclaredMethod("client");
      createClient.setAccessible(true);
      assertSame(client, createClient.invoke(source));
      assertSame(client, createClient.invoke(source));
      builders.verify(() -> ConnectionStringBuilder.createWithAadApplicationCredentials(
          "https://synthetic.kusto.windows.net", "synthetic-client", "synthetic-secret", "synthetic-tenant"));
      builders.verifyNoMoreInteractions();
      clients.verify(() -> ClientFactory.createClient(eq(connection), any(CloseableHttpClient.class)));
    }
  }

  @Test
  void usesQueryOnlySdkWithBoundValuesAndPreservesRawScalarPrecision() throws Exception {
    var columns = new java.util.ArrayList<java.util.Map<String, String>>();
    var row = new java.util.ArrayList<Object>();
    for (String name : ReEvent.FIELDS) {
      columns.add(java.util.Map.of("ColumnName", name, "DataType",
          name.equals("standIn") ? "bool" : name.equals("version") ? "long" : "string"));
      row.add(switch (name) {
        case "insertedTimestamp", "dataOraEvento" -> "2026-10-23T12:00:00.123456789Z"; // Rome wall clock (CEST)
        case "standIn" -> true;
        case "version" -> 42;
        case "status" -> "UNRECOGNIZED";
        default -> null;
      });
    }
    columns.add(java.util.Map.of("ColumnName", "__reTime", "DataType", "datetime"));
    columns.add(java.util.Map.of("ColumnName", "__reId", "DataType", "string"));
    row.add("2026-10-23T12:00:00.123456789Z");
    row.add("stable-row");
    var table = new LinkedHashMap<String, Object>();
    table.put("FrameType", "DataTable");
    table.put("TableName", "PrimaryResult");
    table.put("TableKind", "PrimaryResult");
    table.put("TableId", 0);
    table.put("Columns", columns);
    table.put("Rows", List.of(row));
    String json = new ObjectMapper().writeValueAsString(List.of(table,
        Map.of("FrameType", "DataSetCompletion", "HasErrors", false, "Cancelled", false)));
    when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(json);
    var result = source.read(query());
    assertEquals(1, result.size());
    assertEquals("2026-10-23T10:00:00.123456789Z", result.get(0).fields().get("insertedTimestamp"));
    assertEquals(Instant.parse("2026-10-23T10:00:00.123456789Z"), result.get(0).time());
    assertEquals(42, result.get(0).fields().get("version"));
    assertEquals(true, result.get(0).fields().get("standIn"));
    var properties = org.mockito.ArgumentCaptor.forClass(ClientRequestProperties.class);
    verify(client).executeToJsonResult(eq("synthetic"), eq(ReKql.text(config)), properties.capture());
    assertEquals("synthetic-token", properties.getValue().getParameter("token"));
    assertEquals("pa", properties.getValue().getParameter("pa"));
    assertEquals("nav", properties.getValue().getParameter("nav"));
    assertEquals("datetime(2026-10-23T12:00:00)", properties.getValue().getParameter("fromLocal"));
    assertEquals("datetime(2026-10-23T13:00:00)", properties.getValue().getParameter("toLocal"));
    assertEquals(15000L, properties.getValue().getTimeoutInMilliSec());
    assertEquals(true, properties.getValue().getOption("request_readonly"));
    assertEquals(false, properties.getValue().getOption("results_progressive_enabled"));
    verify(client, never()).executeQuery(anyString(), anyString(), any());
    verify(client, never()).executeMgmt(anyString(), anyString(), any());
  }

  @Test
  void preservesExplicitDynamicNullsForAbsentSourceColumnsInApiContract() throws Exception {
    Set<String> absentFields = Set.of(
        "componente", "tipoVersamento", "fruitore", "erogatore", "parametriSpecificiInterfaccia",
        "esito", "info", "businessProcess", "fruitoreDescr", "erogatoreDescr", "standIn", "version");
    var columns = new java.util.ArrayList<Map<String, String>>();
    var row = new java.util.ArrayList<Object>();
    for (String name : ReEvent.FIELDS) {
      columns.add(Map.of("ColumnName", name, "DataType", absentFields.contains(name) ? "dynamic" : "string"));
      row.add(name.equals("status") ? "UNRECOGNIZED" : null);
    }
    columns.add(Map.of("ColumnName", "__reTime", "DataType", "datetime"));
    columns.add(Map.of("ColumnName", "__reId", "DataType", "string"));
    row.add("2026-10-23T12:00:00Z"); // Rome wall clock (CEST) of the 10:00Z window start
    row.add("stable-row");
    var mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(List.of(
        Map.of("FrameType", "DataTable", "TableName", "PrimaryResult", "TableKind", "PrimaryResult",
            "TableId", 0, "Columns", columns, "Rows", List.of(row)),
        Map.of("FrameType", "DataSetCompletion", "HasErrors", false, "Cancelled", false)));
    when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(json);
    var result = source.read(query());
    assertEquals(1, result.size());
    var event = mapper.readTree(mapper.writeValueAsString(ReEvent.from(result.get(0).fields())));
    assertEquals(36, event.size());
    assertEquals("UNRECOGNIZED", event.path("status").asText());
    for (String field : absentFields) {
      assertTrue(event.has(field), field);
      assertTrue(event.get(field).isNull(), field);
    }
  }

  @Test
  void timesOutCancelsWorkAndSanitizesSdkFailures() throws Exception {
    config.timeoutSeconds = 1;
    CountDownLatch interrupted = new CountDownLatch(1);
    when(client.executeToJsonResult(anyString(), anyString(), any())).thenAnswer(call -> {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException expected) {
        interrupted.countDown();
        throw expected;
      }
      return null;
    });
    long start = System.nanoTime();
    assertStatus(504);
    assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000);
    assertTrue(interrupted.await(2, TimeUnit.SECONDS));
  }

  @Test
  void mapsThrottleTimeoutAndSchemaErrorsWithoutExceptionCauseOrSecret() throws Exception {
    doThrow(new ThrottleException("synthetic-token"))
        .when(client).executeToJsonResult(anyString(), anyString(), any());
    assertStatus(429);
    doThrow(new DataClientException("synthetic-token", "private payload", new SocketTimeoutException("secret")))
        .when(client).executeToJsonResult(anyString(), anyString(), any());
    assertStatus(504);
    doThrow(new DataServiceException("synthetic-token", "private payload schema failure", true))
        .when(client).executeToJsonResult(anyString(), anyString(), any());
    assertStatus(502);
  }

  @Test
  void disabledSourceDoesNotInitializeManagedIdentityOrCallSdk() {
    config.enabled = false;
    assertStatus(503);
    verifyNoInteractions(client);
  }

  @Test
  void rejectsFailedOrCancelledHttp200CompletionInsteadOfReportingAnEmptyWindow() throws Exception {
    for (Map<String, Object> completion : List.of(
        Map.<String, Object>of("FrameType", "DataSetCompletion", "HasErrors", true, "Cancelled", false,
            "OneApiErrors", List.of(Map.of("code", "E_QUERY_RESULT_SET_TOO_LARGE"))),
        Map.<String, Object>of("FrameType", "DataSetCompletion", "HasErrors", false, "Cancelled", true))) {
      String json = emptyV2(completion);
      var sdkResult = new KustoOperationResult(json, "v2");
      assertEquals(0, sdkResult.getPrimaryResults().count(), "SDK ignores the failed completion frame");
      when(client.executeQuery(anyString(), anyString(), any())).thenReturn(sdkResult);
      when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(json);
      assertStatus(502);
    }
  }

  @Test
  void rejectsMissingCompletionAndMapsExplicitCompletionTimeoutWithoutLeakingDetails() throws Exception {
    String timeout = emptyV2(Map.of("FrameType", "DataSetCompletion", "HasErrors", true, "Cancelled", true,
        "OneApiErrors", List.of(Map.of("code", "E_QUERY_EXECUTION_TIMEOUT", "message", "synthetic-token"))));
    when(client.executeQuery(anyString(), anyString(), any())).thenReturn(new KustoOperationResult(timeout, "v2"));
    when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(timeout);
    assertStatus(504);
    var tree = new ObjectMapper().readTree(emptyV2(Map.of(
        "FrameType", "DataSetCompletion", "HasErrors", false, "Cancelled", false)));
    ((com.fasterxml.jackson.databind.node.ArrayNode) tree).remove(1);
    String missing = tree.toString();
    when(client.executeQuery(anyString(), anyString(), any())).thenReturn(new KustoOperationResult(missing, "v2"));
    when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(missing);
    assertStatus(502);
  }

  @Test
  void acceptsEmptyOnlyWithOneUnambiguouslySuccessfulFinalCompletion() throws Exception {
    String success = emptyV2(Map.of(
        "FrameType", "DataSetCompletion", "HasErrors", false, "Cancelled", false));
    when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(success);
    assertTrue(source.read(query()).isEmpty());
    for (Map<String, Object> invalid : List.of(
        Map.<String, Object>of("FrameType", "DataSetCompletion", "HasErrors", false, "Cancelled", false,
            "OneApiErrors", List.of(Map.of("code", "E_QUERY_RESULT_SET_TOO_LARGE"))),
        Map.<String, Object>of("FrameType", "DataSetCompletion", "HasErrors", false),
        Map.<String, Object>of("FrameType", "TableFragment"))) {
      when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(emptyV2(invalid));
      assertStatus(502);
    }
    var frames = (com.fasterxml.jackson.databind.node.ArrayNode) new ObjectMapper().readTree(success);
    frames.add(frames.get(1).deepCopy());
    when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(frames.toString());
    assertStatus(502);
  }

  private String emptyV2(Map<String, Object> completion) throws Exception {
    var columns = new java.util.ArrayList<Map<String, String>>();
    for (String field : ReEvent.FIELDS) {
      columns.add(Map.of("ColumnName", field, "DataType", "string"));
    }
    columns.add(Map.of("ColumnName", "__reTime", "DataType", "datetime"));
    columns.add(Map.of("ColumnName", "__reId", "DataType", "string"));
    return new ObjectMapper().writeValueAsString(List.of(Map.of(
        "FrameType", "DataTable", "TableName", "PrimaryResult", "TableKind", "PrimaryResult",
        "TableId", 0, "Columns", columns, "Rows", List.of()), completion));
  }

  private void assertStatus(int status) {
    var failure = assertThrows(ObservabilityException.class, () -> source.read(query()));
    assertEquals(status, failure.getStatus());
    assertNull(failure.getCause());
    assertFalse(failure.getMessage().contains("synthetic-token"));
    assertFalse(failure.getMessage().contains("private payload"));
  }

  private ReSource.Query query() {
    return new ReSource.Query("pa", "nav", "synthetic-token", from, from.plusSeconds(3600),
        from.plusSeconds(7200), null, null, 101);
  }
}
