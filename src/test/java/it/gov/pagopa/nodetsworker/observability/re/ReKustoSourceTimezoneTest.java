package it.gov.pagopa.nodetsworker.observability.re;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.kusto.data.Client;
import com.microsoft.azure.kusto.data.ClientRequestProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Regression: RE {@code insertedTimestamp} is Europe/Rome wall-clock time without offset. A UTC window
 * compared with it directly missed every event (one hour off in winter, two in summer), so the
 * diagnostic returned zero events for attempts that did exist.
 */
class ReKustoSourceTimezoneTest {
  private final ReKustoSource source = new ReKustoSource();
  private final Client client = mock(Client.class);
  private final ReConfig config = new ReConfig();

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
    config.rowIdColumn = Optional.of("uniqueId");
    config.retentionDays = 90;
    source.config = config;
    source.client = client;
  }

  @AfterEach
  void close() {
    source.close();
  }

  @Test
  void winterAttemptIsFoundWhenSourceStoresRomeWallClockTime() throws Exception {
    // Real attempt: started 09:58:31Z on 2026-11-02, stored by the RE as 10:58:31 (CET, UTC+1).
    when(client.executeToJsonResult(anyString(), anyString(), any()))
        .thenReturn(result("2026-11-02T10:58:30.928405Z"));
    var rows = source.read(query("2026-11-02T09:43:31.172Z", "2026-11-02T10:13:31.770Z"));

    var properties = ArgumentCaptor.forClass(ClientRequestProperties.class);
    verify(client).executeToJsonResult(eq("synthetic"), eq(ReKql.text(config)), properties.capture());
    assertEquals("datetime(2026-11-02T10:43:31.172)", properties.getValue().getParameter("fromLocal"));
    assertEquals("datetime(2026-11-02T11:13:31.77)", properties.getValue().getParameter("toLocal"));
    assertEquals(1, rows.size());
    assertEquals(Instant.parse("2026-11-02T09:58:30.928405Z"), rows.get(0).time());
    assertEquals("2026-11-02T09:58:30.928405Z", rows.get(0).fields().get("insertedTimestamp"));
    assertEquals("2026-11-02T09:58:30.928Z", rows.get(0).fields().get("dataOraEvento"));
  }

  @Test
  void summerWindowUsesTwoHourOffset() throws Exception {
    when(client.executeToJsonResult(anyString(), anyString(), any()))
        .thenReturn(result("2026-08-02T11:58:30.000Z"));
    var rows = source.read(query("2026-08-02T09:43:00Z", "2026-08-02T10:13:00Z"));

    var properties = ArgumentCaptor.forClass(ClientRequestProperties.class);
    verify(client).executeToJsonResult(anyString(), anyString(), properties.capture());
    assertEquals("datetime(2026-08-02T11:43:00)", properties.getValue().getParameter("fromLocal"));
    assertEquals(Instant.parse("2026-08-02T09:58:30Z"), rows.get(0).time());
  }

  @Test
  void seekKeyIsBoundInRomeWallClockTime() throws Exception {
    when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(result("2026-11-02T10:58:31Z"));
    source.read(new ReSource.Query("pa", "nav", "token", Instant.parse("2026-11-02T09:43:00Z"),
        Instant.parse("2026-11-02T10:13:00Z"), Instant.parse("2026-11-02T11:00:00Z"),
        Instant.parse("2026-11-02T09:58:30.928405Z"), "id-0", 101));

    var properties = ArgumentCaptor.forClass(ClientRequestProperties.class);
    verify(client).executeToJsonResult(anyString(), anyString(), properties.capture());
    assertEquals("datetime(2026-11-02T10:58:30.928405)", properties.getValue().getParameter("afterTime"));
    // The watermark compares with ingestion_time(), a real UTC instant: not shifted.
    assertEquals("datetime(2026-11-02T11:00:00Z)", properties.getValue().getParameter("watermark"));
  }

  @Test
  void kqlFiltersOnTheLocalWindowAndNeverOnUtcParameters() {
    String kql = ReKql.text(config);
    assertTrue(kql.contains("fromLocal:datetime,toLocal:datetime"));
    assertTrue(kql.contains("insertedTimestamp >= fromLocal and insertedTimestamp < toLocal"));
    assertFalse(kql.contains("fromUtc"));
    assertFalse(kql.contains("toUtc"));
  }

  @Test
  void rowConvertingOutsideTheWindowIsNeverSurfaced() throws Exception {
    // Autumn overlap: 02:30 local maps to 00:30Z (first occurrence), before a window starting at 00:45Z.
    when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(result("2026-10-25T02:30:00Z"));
    assertTrue(source.read(query("2026-10-25T00:45:00Z", "2026-10-25T01:45:00Z")).isEmpty());
  }

  @Test
  void positionQueryHasNoTokenKeyOrParameterAndStillConvertsTheWindow() throws Exception {
    // Token-less call (verifyPaymentNotice): found by PA + NAV only, same Rome wall-clock conversion.
    when(client.executeToJsonResult(anyString(), anyString(), any())).thenReturn(result("2026-11-02T10:50:00Z"));
    var rows = source.read(new ReSource.Query("pa", "nav", null, Instant.parse("2026-11-02T09:00:00Z"),
        Instant.parse("2026-11-02T10:00:00Z"), Instant.parse("2026-11-02T11:00:00Z"), null, null, 101));

    var kql = ArgumentCaptor.forClass(String.class);
    var properties = ArgumentCaptor.forClass(ClientRequestProperties.class);
    verify(client).executeToJsonResult(eq("synthetic"), kql.capture(), properties.capture());
    assertEquals(ReKql.text(config, true), kql.getValue());
    assertFalse(kql.getValue().contains("paymentToken == token"));
    assertFalse(kql.getValue().contains("token:string"));
    assertTrue(kql.getValue().contains("idDominio == pa and noticeNumber == nav"));
    assertNull(properties.getValue().getParameter("token"));
    assertEquals("datetime(2026-11-02T10:00:00)", properties.getValue().getParameter("fromLocal"));
    assertEquals(1, rows.size());
    assertEquals(Instant.parse("2026-11-02T09:50:00Z"), rows.get(0).time());
  }

  @Test
  void attemptQueryStillRequiresTheToken() {
    String kql = ReKql.text(config, false);
    assertTrue(kql.contains("token:string"));
    assertTrue(kql.contains("idDominio == pa and noticeNumber == nav and paymentToken == token"));
    assertEquals(kql, ReKql.text(config));
  }

  private static ReSource.Query query(String from, String to) {
    return new ReSource.Query("pa", "nav", "token", Instant.parse(from), Instant.parse(to),
        Instant.parse(to).plusSeconds(3600), null, null, 101);
  }

  /** One-row V2 result whose RE timestamps (inserted, event, seek) are the given Rome wall-clock value. */
  private static String result(String romeWallClock) throws Exception {
    var columns = new ArrayList<Map<String, String>>();
    var row = new ArrayList<Object>();
    for (String name : ReEvent.FIELDS) {
      columns.add(Map.of("ColumnName", name, "DataType", "string"));
      row.add(switch (name) {
        case "insertedTimestamp", "dataOraEvento" -> name.equals("dataOraEvento")
            ? romeWallClock.replaceAll("\\.(\\d{3})\\d*Z$", ".$1Z") : romeWallClock;
        default -> null;
      });
    }
    columns.add(Map.of("ColumnName", "__reTime", "DataType", "datetime"));
    columns.add(Map.of("ColumnName", "__reId", "DataType", "string"));
    row.add(romeWallClock);
    row.add("row-1");
    return new ObjectMapper().writeValueAsString(List.of(
        Map.of("FrameType", "DataTable", "TableName", "PrimaryResult", "TableKind", "PrimaryResult",
            "TableId", 0, "Columns", columns, "Rows", List.of(row)),
        Map.of("FrameType", "DataSetCompletion", "HasErrors", false, "Cancelled", false)));
  }
}
