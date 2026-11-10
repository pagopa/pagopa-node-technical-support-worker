package it.gov.pagopa.nodetsworker.observability.re;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.kusto.data.Client;
import com.microsoft.azure.kusto.data.ClientFactory;
import com.microsoft.azure.kusto.data.ClientRequestProperties;
import com.microsoft.azure.kusto.data.KustoResultSetTable;
import com.microsoft.azure.kusto.data.KustoOperationResult;
import com.microsoft.azure.kusto.data.auth.ConnectionStringBuilder;
import com.microsoft.azure.kusto.data.exceptions.DataServiceException;
import com.microsoft.azure.kusto.data.exceptions.ThrottleException;
import com.microsoft.azure.kusto.data.instrumentation.Tracer;
import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;

/** Query-only SDK client, lazy application credentials, bounded worker/socket/server deadlines, no retries. */
@ApplicationScoped
public class ReKustoSource implements ReSource {
  private static final ObjectMapper RESPONSE_MAPPER = new ObjectMapper();
  @Inject ReConfig config;
  volatile Client client;
  private ThreadPoolExecutor executor;

  @Override
  public List<Row> read(Query query) {
    config.requireReady();
    Future<List<Row>> work;
    try {
      work = executor().submit(() -> execute(query));
    } catch (RejectedExecutionException busy) {
      throw ReErrors.error(429, "RE_THROTTLED", "Limite richieste RE raggiunto.");
    }
    try {
      return work.get(config.timeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException timeout) {
      work.cancel(true);
      throw timeout();
    } catch (InterruptedException cancelled) {
      work.cancel(true);
      Thread.currentThread().interrupt();
      throw timeout();
    } catch (ExecutionException failed) {
      Throwable cause = failed.getCause();
      if (cause instanceof ObservabilityException safe) {
        throw safe;
      }
      if (cause instanceof ThrottleException) {
        throw ReErrors.error(429, "RE_THROTTLED", "Sorgente RE temporaneamente limitata.");
      }
      if (cause instanceof DataServiceException service) {
        Integer status = service.getStatusCode();
        if (Integer.valueOf(429).equals(status)) {
          throw ReErrors.error(429, "RE_THROTTLED", "Sorgente RE temporaneamente limitata.");
        }
        if (Integer.valueOf(408).equals(status) || Integer.valueOf(504).equals(status)) {
          throw timeout();
        }
      }
      for (int i = 0; cause != null && i < 8; i++, cause = cause.getCause()) {
        if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException) {
          throw timeout();
        }
      }
      throw ReErrors.source();
    }
  }

  private List<Row> execute(Query query) throws Exception {
    ClientRequestProperties properties = new ClientRequestProperties();
    properties.setClientRequestId("ReDiagnostic;" + UUID.randomUUID());
    properties.setTimeoutInMilliSec(config.timeoutSeconds * 1000L);
    properties.setOption("request_readonly", true);
    properties.setOption("queryconsistency", "strongconsistency");
    properties.setOption("deferpartialqueryfailures", false);
    properties.setOption("results_progressive_enabled", false);
    properties.setOption("truncationmaxrecords", query.take());
    properties.setOption("truncationmaxsize", 64 * 1024 * 1024);
    properties.setParameter("pa", query.pa());
    properties.setParameter("nav", query.notice());
    boolean position = query.token() == null;
    if (!position) {
      properties.setParameter("token", query.token());
    }
    // insertedTimestamp is Europe/Rome wall-clock time: bind the window and seek key in that frame.
    properties.setParameter("fromLocal", ReTime.localLiteral(query.from()));
    properties.setParameter("toLocal", ReTime.localLiteral(query.to()));
    properties.setParameter("watermark", datetime(query.watermark()));
    properties.setParameter("hasAfter", query.afterTime() != null);
    properties.setParameter("afterTime",
        ReTime.localLiteral(query.afterTime() == null ? query.from() : query.afterTime()));
    properties.setParameter("afterId", query.afterId() == null ? "" : query.afterId());
    properties.setParameter("takeRows", query.take());
    // The SDK's parsed V2 result discards completion/error frames. Validate raw JSON first.
    // Fixed declared-query KQL routes executeToJsonResult to the V2 query endpoint, never management.
    String raw = client().executeToJsonResult(config.database.orElseThrow(), ReKql.text(config, position), properties);
    var result = completedResult(raw);
    KustoResultSetTable table = result.getPrimaryResults();
    if (table == null || table.getColumns() == null || table.count() > query.take()) {
      throw ReErrors.source();
    }
    Set<String> columns = new HashSet<>();
    Arrays.stream(table.getColumns()).forEach(column -> columns.add(column.getColumnName()));
    if (!columns.containsAll(ReEvent.FIELDS) || !columns.containsAll(List.of("__reTime", "__reId"))) {
      throw ReErrors.source();
    }
    List<Row> rows = new ArrayList<>();
    while (table.hasNext()) {
      if (Thread.currentThread().isInterrupted()) {
        throw timeout();
      }
      table.next();
      var fields = new LinkedHashMap<String, Object>();
      for (String field : ReEvent.FIELDS) {
        Object value = table.getObject(field);
        fields.put(field, ReTime.LOCAL_FIELDS.contains(field) ? ReTime.fieldToUtc(value) : value);
      }
      Object time = table.getObject("__reTime");
      Object id = table.getObject("__reId");
      if (!(time instanceof String rawTime) || !(id instanceof String rawId)) {
        throw ReErrors.source();
      }
      Instant utc = ReTime.toUtc(Instant.parse(rawTime));
      // Local-time filtering is exact except across the autumn overlap, where a row at the window edge can
      // map just outside [from, to): never surface it, the caller requires every row inside the window.
      if (utc.isBefore(query.from()) || !utc.isBefore(query.to())) {
        continue;
      }
      rows.add(new Row(utc, rawId, fields));
    }
    return List.copyOf(rows);
  }

  private KustoOperationResult completedResult(String raw) throws Exception {
    if (raw == null || raw.length() > 64 * 1024 * 1024) {
      throw ReErrors.source();
    }
    JsonNode frames = RESPONSE_MAPPER.readTree(raw);
    if (frames == null || !frames.isArray() || frames.isEmpty()) {
      throw ReErrors.source();
    }
    boolean completed = false;
    int primaryTables = 0;
    for (int i = 0; i < frames.size(); i++) {
      JsonNode frame = frames.get(i);
      String type = frame.path("FrameType").asText();
      if ("DataSetCompletion".equals(type)) {
        if (completed || i != frames.size() - 1 || !frame.path("HasErrors").isBoolean()
            || !frame.path("Cancelled").isBoolean()) {
          throw ReErrors.source();
        }
        completed = true;
        JsonNode errors = frame.get("OneApiErrors");
        if (errors != null && !errors.isNull() && !errors.isArray()) {
          throw ReErrors.source();
        }
        if (frame.path("HasErrors").booleanValue() || frame.path("Cancelled").booleanValue()
            || (errors != null && errors.isArray() && !errors.isEmpty())) {
          if (errors != null && errors.isArray()) {
            for (JsonNode error : errors) {
              if ("E_QUERY_EXECUTION_TIMEOUT".equals(error.path("code").asText())) {
                throw timeout();
              }
            }
          }
          throw ReErrors.source();
        }
      } else if ("DataTable".equals(type)) {
        if ("PrimaryResult".equals(frame.path("TableKind").asText())) {
          primaryTables++;
        }
      } else if (!"DataSetHeader".equals(type) || i != 0) {
        // Progressive fragments or unknown frames are not a verified complete result.
        throw ReErrors.source();
      }
    }
    if (!completed || primaryTables != 1) {
      throw ReErrors.source();
    }
    return new KustoOperationResult(raw, "v2");
  }

  private synchronized ThreadPoolExecutor executor() {
    if (executor == null) {
      executor = new ThreadPoolExecutor(0, config.maxConcurrent, 30, TimeUnit.SECONDS,
          new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "re-diagnostic");
            thread.setDaemon(true);
            return thread;
          });
    }
    return executor;
  }

  private synchronized Client client() {
    if (client == null) {
      try {
        // Kusto's global SDK tracer can attach exception details; explicitly keep it uninitialized/no-op.
        Tracer.initializeTracer(null);
        int timeoutMillis = config.timeoutSeconds * 1000;
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(timeoutMillis).setConnectionRequestTimeout(timeoutMillis)
            .setSocketTimeout(timeoutMillis).build();
        var http = HttpClients.custom().setDefaultRequestConfig(requestConfig)
            .disableAutomaticRetries().disableRedirectHandling().build();
        client = ClientFactory.createClient(
            ConnectionStringBuilder.createWithAadApplicationCredentials(
                config.endpoint.orElseThrow(), config.clientId.orElseThrow(),
                config.clientSecret.orElseThrow(), config.tenantId.orElseThrow()), http);
      } catch (Exception unavailable) {
        throw ReErrors.config();
      }
    }
    return client;
  }

  @PreDestroy
  void close() {
    if (executor != null) {
      executor.shutdownNow();
    }
    if (client != null) {
      try {
        client.close();
      } catch (Exception ignored) {
        // Closing must never expose SDK diagnostics.
      }
    }
  }

  private static ObservabilityException timeout() {
    return ReErrors.error(504, "RE_TIMEOUT", "Tempo massimo di consultazione RE superato.");
  }

  private static String datetime(Instant value) {
    // The SDK's LocalDateTime overload formats only seven fractional digits.
    return "datetime(" + value + ")";
  }
}
