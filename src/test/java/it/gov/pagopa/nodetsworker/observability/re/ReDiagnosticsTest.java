package it.gov.pagopa.nodetsworker.observability.re;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.service.Caller;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReDiagnosticsTest {
  private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
  private static final String FROM = "2026-10-23T10:00:00Z";
  private static final String TO = "2026-10-23T11:00:00Z";
  private final ProjectionRepository projection = mock(ProjectionRepository.class);
  private final ReSource source = mock(ReSource.class);
  private final ReConfig config = new ReConfig();
  private final ReDiagnostics service = new ReDiagnostics();
  private final Caller caller = Caller.implicit();

  @BeforeEach
  void setup() {
    config.enabled = true;
    config.schemaVerified = true;
    config.timezoneVerified = true;
    config.orderingVerified = true;
    config.watermarkVerified = true;
    config.retentionVerified = true;
    config.personalDataApproved = true;
    config.endpoint = Optional.of("https://synthetic.kusto.windows.net");
    config.clientId = Optional.of("synthetic-client");
    config.clientSecret = Optional.of("synthetic-secret");
    config.tenantId = Optional.of("synthetic-tenant");
    config.database = Optional.of("synthetic");
    config.table = Optional.of("ReEvent");
    config.rowIdColumn = Optional.of("technicalRowId");
    config.retentionDays = 2;
    service.config = config;
    service.projection = projection;
    service.source = source;
    service.clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service.cursors = new ReCursors();
    service.mapper = new ObjectMapper();
    service.limiter = new ReLimiter();
    when(projection.findReContext(eq("attempt"), any())).thenReturn(Optional.of(context(NOW.plusSeconds(600))));
    when(source.read(any())).thenReturn(List.of());
  }

  @Test
  void disabledOrUnverifiedNeverQueriesExternalSource() {
    config.enabled = false;
    assertStatus(503, () -> read(FROM, TO, null));
    config.enabled = true;
    config.watermarkVerified = false;
    assertStatus(503, () -> read(FROM, TO, null));
    config.watermarkVerified = true;
    config.rowIdColumn = Optional.of("table | union *");
    assertStatus(503, () -> read(FROM, TO, null));
    verifyNoInteractions(source);
  }

  @Test
  void requiresExplicitUtcHalfOpenWindowAtMost24Hours() {
    for (var window : List.of(new String[]{null, TO}, new String[]{FROM, null},
        new String[]{TO, FROM}, new String[]{FROM, FROM}, new String[]{"2026-10-23T10:00:00+02:00", TO},
        new String[]{"2026-10-23T10:00:60Z", TO}, new String[]{"2026-10-22T24:00:00Z", TO},
        new String[]{"invalid", TO}, new String[]{"2026-10-21T10:00:00Z", TO},
        new String[]{FROM, "2026-10-24T12:00:00Z"})) {
      assertStatus(400, () -> read(window[0], window[1], null));
    }
    assertStatus(400, () -> service.read(caller, "attempt", null));
    verifyNoInteractions(source);
  }

  @Test
  void distinguishesMissingExpiredAndMissingCorrelationWithoutFallback() {
    when(projection.findReContext(anyString(), any())).thenReturn(Optional.empty());
    assertStatus(404, () -> read(FROM, TO, null));
    when(projection.findReContext(anyString(), any())).thenReturn(Optional.of(context(NOW)));
    assertStatus(410, () -> read(FROM, TO, null));
    when(projection.findReContext(anyString(), any())).thenReturn(Optional.of(
        new ReContext("attempt", "pa", "nav", null, NOW.plusSeconds(60))));
    assertStatus(502, () -> read(FROM, TO, null));
    verifyNoInteractions(source);
  }

  @Test
  void sendsOnlyAndKeysAndHalfOpenUtcBoundsAndFrozenIngestionWatermark() {
    read(FROM, TO, null);
    var captor = org.mockito.ArgumentCaptor.forClass(ReSource.Query.class);
    verify(source).read(captor.capture());
    var q = captor.getValue();
    assertEquals("pa", q.pa());
    assertEquals("nav", q.notice());
    assertEquals("synthetic-secret-token", q.token());
    assertEquals(Instant.parse(FROM), q.from());
    assertEquals(Instant.parse(TO), q.to());
    assertEquals(NOW, q.watermark());
    assertEquals(101, q.take());
    String kql = ReKql.text(config);
    assertTrue(kql.contains("idDominio == pa and noticeNumber == nav and paymentToken == token"));
    assertTrue(kql.contains("insertedTimestamp >= fromLocal and insertedTimestamp < toLocal"));
    assertTrue(kql.contains("ingestion_time() <= watermark"));
    assertFalse(kql.contains("synthetic-secret-token"));
    assertFalse(kql.contains("offset"));
    for (String field : List.of("tipoEvento", "esito", "status", "componente", "categoriaEvento")) {
      assertFalse(kql.contains("where " + field));
    }
  }

  @Test
  void projectsOnlyActualSourceColumnsAndKeepsAbsentContractFieldsAsNull() {
    Set<String> sourceColumns = Set.of(
        "insertedTimestamp", "categoriaEvento", "sottoTipoEvento", "idDominio", "iuv",
        "ccp", "psp", "tipoEvento", "stazione", "canale", "sessionId", "payload", "pspDescr",
        "noticeNumber", "creditorReferenceId", "paymentToken", "sessionIdOriginal",
        "dataOraEvento", "uniqueId", "serviceIdentifier", "status");
    String expected = ReEvent.FIELDS.stream()
        .map(field -> sourceColumns.contains(field) ? field : field + " = dynamic(null)")
        .collect(Collectors.joining(", "));
    String kql = ReKql.text(config);
    assertEquals(33, ReEvent.FIELDS.size());
    assertEquals(12, ReEvent.FIELDS.stream().filter(field -> !sourceColumns.contains(field)).count());
    assertEquals(expected + ", __reTime, __reId",
        kql.substring(kql.indexOf("| project ") + "| project ".length()));
    assertTrue(kql.contains("strcmp(__reId, afterId) > 0"));
    assertFalse(kql.contains("__reId > afterId"));
  }

  @Test
  void distinguishesEmptyWindowPartialRetentionAndEntirelyOutsideRetention() {
    var empty = read(FROM, TO, null);
    assertEquals("WINDOW_ONLY", empty.coverage());
    assertTrue(empty.events().isEmpty());
    var partial = read("2026-10-21T11:00:00Z", "2026-10-21T13:00:00Z", null);
    assertEquals("RETENTION_CLIPPED", partial.coverage());
    assertEquals("2026-10-21T12:00:00Z", partial.window().from());
    assertTrue(partial.warnings().contains("RETENTION_CLIPPED"));
    var outside = read("2026-10-20T11:00:00Z", "2026-10-20T13:00:00Z", null);
    assertEquals("RETENTION_CLIPPED", outside.coverage());
    assertTrue(outside.warnings().contains("OUTSIDE_RETENTION"));
    assertTrue(outside.events().isEmpty());
    verify(source, times(2)).read(any());
  }

  @Test
  void cursorUsesStableSeekSnapshotAndRejectsTamperingExpiryAndBindingChanges() {
    when(source.read(any())).thenAnswer(invocation -> {
      ReSource.Query q = invocation.getArgument(0);
      if (q.afterId() == null) {
        return rows(101);
      }
      assertEquals("099", q.afterId());
      assertEquals(Instant.parse(FROM), q.afterTime());
      assertEquals(NOW, q.watermark());
      return List.of(row("100"));
    });
    var first = read(FROM, TO, null);
    assertEquals(100, first.events().size());
    assertNotNull(first.nextCursor());
    assertFalse(first.truncated());
    service.clock = Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC);
    var second = read(FROM, TO, first.nextCursor());
    assertEquals(1, second.events().size());
    assertNull(second.nextCursor());
    assertStatus(400, () -> read(FROM, TO, first.nextCursor() + "x"));
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    String cursor = first.nextCursor();
    char aliasedLast = alphabet.charAt(alphabet.indexOf(cursor.charAt(cursor.length() - 1)) + 1);
    assertStatus(400, () -> read(FROM, TO, cursor.substring(0, cursor.length() - 1) + aliasedLast));
    assertStatus(400, () -> read(FROM, TO, (cursor.charAt(0) == 'A' ? "B" : "A") + cursor.substring(1)));
    assertStatus(400, () -> read("2026-10-23T09:00:00Z", TO, first.nextCursor()));
    config.pageSize = 99;
    assertStatus(400, () -> read(FROM, TO, first.nextCursor()));
    config.pageSize = 100;
    service.clock = Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC);
    assertStatus(400, () -> read(FROM, TO, first.nextCursor()));
  }

  @Test
  void capsWholeConsultationAndBlocksAmbiguousOrUnorderedSeekKeys() {
    config.maxRows = 100;
    when(source.read(any())).thenReturn(rows(101));
    var result = read(FROM, TO, null);
    assertEquals("LIMIT_REACHED", result.coverage());
    assertTrue(result.truncated());
    assertNull(result.nextCursor());
    when(source.read(any())).thenReturn(List.of(row("same"), row("same")));
    assertStatus(502, () -> read(FROM, TO, null));
    when(source.read(any())).thenReturn(List.of(row("b"), row("a")));
    assertStatus(502, () -> read(FROM, TO, null));
  }

  @Test
  void boundsSerializedResponseRatherThanOnlyDecodedPayloadBytes() throws Exception {
    String payload = java.util.Base64.getEncoder().encodeToString(new byte[256 * 1024]);
    var rows = new ArrayList<ReSource.Row>();
    for (int i = 0; i < 10; i++) {
      rows.add(new ReSource.Row(Instant.parse(FROM), String.format("%03d", i),
          Map.of("insertedTimestamp", FROM, "payload", payload)));
    }
    when(source.read(any())).thenReturn(rows);
    var response = read(FROM, TO, null);
    assertTrue(response.events().size() < 10);
    assertNotNull(response.nextCursor());
    assertTrue(service.mapper.writeValueAsBytes(response).length <= 5 * 1024 * 1024);
  }

  @Test
  void sanitizesUpstreamErrorsAndDoesNotPutSecretsInToString() {
    when(source.read(any())).thenThrow(new IllegalStateException("synthetic-secret-token private payload"));
    var error = assertThrows(ObservabilityException.class, () -> read(FROM, TO, null));
    assertEquals(502, error.getStatus());
    assertNull(error.getCause());
    assertFalse(error.getMessage().contains("synthetic-secret"));
    assertFalse(context(NOW).toString().contains("synthetic-secret"));
  }

  @Test
  void rechecksAttemptExpiryAfterTheSourceReadAndPreservesDistinctTechnicalRowsWithSameEventId() {
    when(projection.findReContext(eq("attempt"), any()))
        .thenReturn(Optional.of(context(NOW.plusSeconds(600))), Optional.of(context(NOW)));
    assertStatus(410, () -> read(FROM, TO, null));
    when(projection.findReContext(eq("attempt"), any())).thenReturn(Optional.of(context(NOW.plusSeconds(600))));
    when(source.read(any())).thenReturn(List.of(
        new ReSource.Row(Instant.parse(FROM), "a", Map.of("uniqueId", "same-event-id")),
        new ReSource.Row(Instant.parse(FROM), "b", Map.of("uniqueId", "same-event-id"))));
    assertEquals(2, read(FROM, TO, null).events().size());
  }

  @Test
  void bindsCursorToAttemptAndCallerAndKeepsClippedWindowStable() {
    String from = "2026-10-21T11:00:00Z";
    String to = "2026-10-21T13:00:00Z";
    var clippedRows = new ArrayList<ReSource.Row>();
    for (int i = 0; i < 101; i++) {
      clippedRows.add(new ReSource.Row(NOW.minus(Duration.ofDays(2)), String.format("%03d", i), Map.of()));
    }
    when(source.read(any())).thenReturn(clippedRows);
    var first = read(from, to, null);
    assertNotNull(first.nextCursor());
    when(projection.findReContext(eq("other"), any())).thenReturn(
        Optional.of(new ReContext("other", "pa", "nav", "synthetic-secret-token", NOW.plusSeconds(600))));
    assertStatus(400, () -> service.read(caller, "other", new ReRequest(from, to, first.nextCursor())));
    assertStatus(400, () -> service.read(new Caller("different", "viewer", null, false, false),
        "attempt", new ReRequest(from, to, first.nextCursor())));
    service.clock = Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC);
    when(source.read(any())).thenReturn(List.of());
    assertEquals(first.window(), read(from, to, first.nextCursor()).window());
  }

  @Test
  void positionScopeDropsTheTokenKeyAndSaysSo() {
    var response = service.read(caller, "attempt", new ReRequest(FROM, TO, null, ReRequest.POSITION));
    var captor = org.mockito.ArgumentCaptor.forClass(ReSource.Query.class);
    verify(source).read(captor.capture());
    assertNull(captor.getValue().token());
    assertEquals("pa", captor.getValue().pa());
    assertEquals("nav", captor.getValue().notice());
    assertTrue(response.warnings().contains("POSITION_SCOPE"));
    // The default scope keeps the three-key correlation and does not carry the warning.
    var attempt = read(FROM, TO, null);
    assertFalse(attempt.warnings().contains("POSITION_SCOPE"));
    verify(source, times(2)).read(captor.capture());
    assertEquals("synthetic-secret-token", captor.getValue().token());
  }

  @Test
  void rejectsUnknownScopeAndCursorsAcrossScopes() {
    assertStatus(400, () -> service.read(caller, "attempt", new ReRequest(FROM, TO, null, "ALL")));
    assertStatus(400, () -> service.read(caller, "attempt", new ReRequest(FROM, TO, null, "position")));
    verifyNoInteractions(source);
    when(source.read(any())).thenReturn(rows(101));
    var first = service.read(caller, "attempt", new ReRequest(FROM, TO, null, ReRequest.POSITION));
    assertNotNull(first.nextCursor());
    assertStatus(400, () -> service.read(caller, "attempt", new ReRequest(FROM, TO, first.nextCursor())));
  }

  @Test
  void limitsRateAndConcurrencyWithoutUnboundedWaiting() {
    ReLimiter limiter = new ReLimiter();
    try (var lease = limiter.acquire(1, 60, NOW)) {
      assertStatus(429, () -> limiter.acquire(1, 60, NOW));
    }
    try (var lease = limiter.acquire(1, 60, NOW)) {
      assertNotNull(lease);
    }
    ReLimiter rate = new ReLimiter();
    try (var lease = rate.acquire(1, 1, NOW)) {
      assertNotNull(lease);
    }
    assertStatus(429, () -> rate.acquire(1, 1, NOW));
  }

  private ReResponse read(String from, String to, String cursor) {
    return service.read(caller, "attempt", new ReRequest(from, to, cursor));
  }

  private ReContext context(Instant expiry) {
    return new ReContext("attempt", "pa", "nav", "synthetic-secret-token", expiry);
  }

  private List<ReSource.Row> rows(int size) {
    var result = new ArrayList<ReSource.Row>();
    for (int i = 0; i < size; i++) {
      result.add(row(String.format("%03d", i)));
    }
    return result;
  }

  private ReSource.Row row(String id) {
    return new ReSource.Row(Instant.parse(FROM), id, Map.of("insertedTimestamp", FROM, "uniqueId", id));
  }

  private void assertStatus(int status, org.junit.jupiter.api.function.Executable operation) {
    assertEquals(status, assertThrows(ObservabilityException.class, operation).getStatus());
  }
}
