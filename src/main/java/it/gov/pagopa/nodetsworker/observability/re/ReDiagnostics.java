package it.gov.pagopa.nodetsworker.observability.re;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.service.Caller;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jboss.logging.Logger;

/** Bounded read-only consultation. No FSM/category/primitive filters or alternative correlations. */
@ApplicationScoped
public class ReDiagnostics {
  static final int RESPONSE_BYTES = 5 * 1024 * 1024;
  private static final Logger LOG = Logger.getLogger(ReDiagnostics.class);
  @Inject ReConfig config;
  @Inject ProjectionRepository projection;
  @Inject ReSource source;
  @Inject ReCursors cursors;
  @Inject ReLimiter limiter;
  @Inject ObjectMapper mapper;
  Clock clock = Clock.systemUTC();

  public ReResponse read(Caller caller, String attemptId, ReRequest request) {
    long started = System.nanoTime();
    String correlation = UUID.randomUUID().toString();
    int outcome = 500;
    int count = 0;
    try {
      ReResponse response = consult(caller, attemptId, request);
      outcome = 200;
      count = response.events().size();
      return response;
    } catch (ObservabilityException failure) {
      outcome = failure.getStatus();
      throw failure;
    } catch (RuntimeException failure) {
      outcome = 502;
      throw ReErrors.source();
    } finally {
      // Never log exceptions, request bodies, source keys, SDK queries or event content.
      LOG.infof("RE_DIAGNOSTIC correlation=%s durationMs=%d outcome=%d count=%d",
          correlation, (System.nanoTime() - started) / 1_000_000, outcome, count);
    }
  }

  private ReResponse consult(Caller caller, String attemptId, ReRequest request) {
    config.requireReady();
    if (request == null) {
      throw ReErrors.invalid();
    }
    if (request.scope() != null
        && !ReRequest.ATTEMPT.equals(request.scope()) && !ReRequest.POSITION.equals(request.scope())) {
      throw ReErrors.invalid();
    }
    Instant requestedFrom = utc(request.from());
    Instant requestedTo = utc(request.to());
    if (!requestedFrom.isBefore(requestedTo)
        || Duration.between(requestedFrom, requestedTo).compareTo(Duration.ofHours(24)) > 0) {
      throw ReErrors.invalid();
    }
    Instant now = clock.instant();
    ReContext context = context(caller, attemptId, now);
    String binding = caller.id() + "|" + context.attemptId() + "|"
        + requestedFrom + "|" + requestedTo + "|" + (request.positionScope() ? ReRequest.POSITION : ReRequest.ATTEMPT)
        + "|" + config.binding();
    ReCursors.Entry state;
    if (request.cursor() != null) {
      state = cursors.resolve(request.cursor(), binding, now);
    } else {
      Instant oldest = now.minus(Duration.ofDays(config.retentionDays));
      Instant from = requestedFrom.isBefore(oldest) ? oldest : requestedFrom;
      Instant to = requestedTo.isAfter(now) ? now : requestedTo;
      boolean clipped = requestedFrom.isBefore(oldest);
      if (!from.isBefore(to)) {
        return response(context.attemptId(), now,
            new ReResponse.Window(requestedFrom.toString(), requestedTo.toString()),
            clipped ? "RETENTION_CLIPPED" : "WINDOW_ONLY", List.of(), null, false,
            List.of(clipped ? "OUTSIDE_RETENTION" : "OUTSIDE_AVAILABLE_WINDOW"));
      }
      state = new ReCursors.Entry(binding, from, to, now,
          now.plusSeconds(config.cursorTtlSeconds), null, null, 0, clipped);
    }
    List<String> warnings = new ArrayList<>();
    if (request.positionScope()) {
      warnings.add("POSITION_SCOPE");
    }
    if (state.clipped()) {
      warnings.add("RETENTION_CLIPPED");
    }
    if (requestedTo.isAfter(state.to())) {
      warnings.add("WINDOW_CLIPPED_TO_NOW");
    }
    int remaining = config.maxRows - state.count();
    if (remaining <= 0) {
      throw ReErrors.invalid();
    }
    int pageLimit = Math.min(config.pageSize, remaining);
    List<ReSource.Row> rows;
    try (var lease = limiter.acquire(config.maxConcurrent, config.requestsPerMinute, now)) {
      rows = source.read(new ReSource.Query(context.pa(), context.notice(), request.positionScope() ? null : context.token(),
          state.from(), state.to(), state.watermark(), state.afterTime(), state.afterId(), pageLimit + 1));
    }
    if (rows == null || rows.size() > pageLimit + 1) {
      throw ReErrors.source();
    }
    validateOrder(rows, state);
    List<ReEvent> events = new ArrayList<>();
    int bytes = 0;
    // Reserve envelope, warning and fixed-length cursor overhead; check exact final serialization too.
    for (int i = 0; i < Math.min(rows.size(), pageLimit); i++) {
      ReEvent event = ReEvent.from(rows.get(i).fields());
      int eventBytes = serializedSize(event);
      if (bytes + eventBytes + 8192 > RESPONSE_BYTES) {
        if (events.isEmpty()) {
          throw ReErrors.source();
        }
        warnings.add("RESPONSE_BYTE_LIMIT");
        break;
      }
      events.add(event);
      bytes += eventBytes + 1;
    }
    int consulted = state.count() + events.size();
    boolean more = rows.size() > events.size();
    boolean truncated = more && consulted >= config.maxRows;
    String nextCursor = null;
    if (truncated) {
      warnings.add("CONSULTATION_ROW_LIMIT");
    } else if (more && !events.isEmpty()) {
      var last = rows.get(events.size() - 1);
      nextCursor = cursors.issue(new ReCursors.Entry(binding, state.from(), state.to(),
          state.watermark(), state.expires(), last.time(), last.id(), consulted, state.clipped()), now);
    }
    ReContext retained = context(caller, attemptId, clock.instant());
    if (!Objects.equals(context, retained)) {
      throw ReErrors.source();
    }
    ReResponse result = response(context.attemptId(), clock.instant(),
        new ReResponse.Window(state.from().toString(), state.to().toString()),
        truncated ? "LIMIT_REACHED" : state.clipped() ? "RETENTION_CLIPPED" : "WINDOW_ONLY",
        List.copyOf(events), nextCursor, truncated, List.copyOf(warnings));
    if (serializedSize(result) > RESPONSE_BYTES) {
      throw ReErrors.source();
    }
    return result;
  }

  private ReContext context(Caller caller, String attemptId, Instant now) {
    if (attemptId == null || !attemptId.matches("[A-Za-z0-9_-]{1,64}")) {
      throw ReErrors.error(404, "RE_ATTEMPT_NOT_FOUND", "Tentativo non disponibile.");
    }
    ReContext context = projection.findReContext(attemptId, caller.allowedPa())
        .orElseThrow(() -> ReErrors.error(404, "RE_ATTEMPT_NOT_FOUND", "Tentativo non disponibile."));
    if (context.expiresAt() == null || !context.expiresAt().isAfter(now)) {
      throw ReErrors.error(410, "RE_ATTEMPT_EXPIRED", "Tentativo scaduto per retention.");
    }
    if (blank(context.pa()) || blank(context.notice()) || blank(context.token())) {
      throw ReErrors.source();
    }
    return context;
  }

  private static void validateOrder(List<ReSource.Row> rows, ReCursors.Entry state) {
    Instant previousTime = state.afterTime();
    String previousId = state.afterId();
    for (var row : rows) {
      if (row == null || row.time() == null || row.id() == null || row.id().isBlank()
          || row.id().length() > 256 || row.fields() == null
          || row.time().isBefore(state.from()) || !row.time().isBefore(state.to())
          || (previousTime != null && (row.time().isBefore(previousTime)
          || (row.time().equals(previousTime) && row.id().compareTo(previousId) <= 0)))) {
        throw ReErrors.source();
      }
      previousTime = row.time();
      previousId = row.id();
    }
  }

  private static Instant utc(String value) {
    if (value == null || value.length() > 40
        || !value.matches("\\d{4}-\\d{2}-\\d{2}T(?:[01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d(\\.\\d{1,9})?Z")) {
      throw ReErrors.invalid();
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException invalid) {
      throw ReErrors.invalid();
    }
  }

  private int serializedSize(Object value) {
    try {
      return mapper.writeValueAsBytes(value).length;
    } catch (JsonProcessingException invalid) {
      throw ReErrors.source();
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static ReResponse response(String id, Instant at, ReResponse.Window window, String coverage,
      List<ReEvent> events, String cursor, boolean truncated, List<String> warnings) {
    return new ReResponse(id, "RE", at.toString(), window, coverage, events, cursor, truncated, warnings);
  }
}
