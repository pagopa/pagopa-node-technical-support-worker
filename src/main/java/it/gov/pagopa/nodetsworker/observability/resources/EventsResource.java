package it.gov.pagopa.nodetsworker.observability.resources;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.service.Caller;
import it.gov.pagopa.nodetsworker.observability.service.CallerResolver;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityConfig;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityService;
import it.gov.pagopa.nodetsworker.observability.service.QuerySupport;
import it.gov.pagopa.nodetsworker.observability.sse.SseClient;
import it.gov.pagopa.nodetsworker.observability.sse.SseHub;
import it.gov.pagopa.nodetsworker.observability.dto.SseContract;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** The SSE stream {@code GET /api/events}: ready, payment/aggregate deltas, replay and heartbeats. */
@Path("/api/events")
public class EventsResource {

  private static final Set<String> EVENT_PARAMS = eventParams();

  @Inject
  ObservabilityService service;

  @Inject
  CallerResolver callerResolver;

  @Inject
  SseHub hub;

  @Inject
  ObservabilityConfig config;

  @GET
  @Blocking
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @APIResponse(responseCode = "200",
      description = "SSE frames: event type in event:, JSON envelope in data:. Heartbeats are comments.",
      content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS,
          schema = @Schema(oneOf = {SseContract.Ready.class, SseContract.PaymentDelta.class,
              SseContract.AggregateDelta.class})))
  public Multi<OutboundSseEvent> events(@Context UriInfo uriInfo, @Context Sse sse,
      @HeaderParam("Last-Event-ID") String lastEventId) {
    hub.bindSse(sse);
    Caller caller = callerResolver.resolve();
    Map<String, String> q = QuerySupport.validate(uriInfo.getQueryParameters(), EVENT_PARAMS);

    String scopeType = q.get("scope");
    if (scopeType == null || !Set.of("payment", "payment-list", "overview", "creditor", "station").contains(scopeType)) {
      throw Errors.invalidRequest();
    }
    if (q.containsKey("live") && !"overview".equals(scopeType)) {
      throw Errors.invalidRequest();
    }
    String canonicalPaymentKeys = null;
    if ("payment-list".equals(scopeType)) {
      if (q.size() != 2 || q.get("keys") == null) {
        throw Errors.invalidRequest();
      }
      var paymentKeys = QuerySupport.parsePaymentKeys(q.get("keys"));
      canonicalPaymentKeys = QuerySupport.formatPaymentKeys(paymentKeys);
      for (var key : paymentKeys) {
        if (service.paymentRevisionIfVisible(caller, key.paFiscalCode(), key.noticeNumber()) == null) {
          throw Errors.notFound("Pagamento non disponibile.");
        }
      }
    } else {
      if (q.containsKey("keys")) {
        throw Errors.invalidRequest();
      }
      validateScope(caller, scopeType, q);
    }
    if (!"payment".equals(scopeType) && !"payment-list".equals(scopeType)) {
      QuerySupport.checkAggregate(q);
    }

    Map<String, String> scope = new TreeMap<>(q);
    if (canonicalPaymentKeys != null) {
      scope.put("keys", canonicalPaymentKeys);
    }
    SseHub.ReplayCursor cursor = hub.resolveCursor(lastEventId, caller, scope);

    long readyRevision;
    Instant from = null;
    Instant to = null;
    if ("payment".equals(scopeType)) {
      Long revision = service.paymentRevisionIfVisible(caller, q.get("paFiscalCode"), q.get("noticeNumber"));
      if (revision == null && cursor == null) {
        throw Errors.notFound("Pagamento non disponibile.");
      }
      readyRevision = revision != null ? revision : 0L;
    } else if ("payment-list".equals(scopeType)) {
      readyRevision = service.revision();
    } else {
      if (cursor != null) {
        from = cursor.from();
        to = cursor.to();
      } else {
        Instant[] window = QuerySupport.window(q, Instant.now(), config);
        from = window[0];
        to = window[1];
      }
      readyRevision = service.revision();
    }

    SseClient client = new SseClient(UUID.randomUUID().toString(), caller, scope, from, to);
    List<OutboundSseEvent> initial = hub.registerAndInitialEvents(client, lastEventId, readyRevision);

    Multi<OutboundSseEvent> heartbeats = Multi.createFrom()
        .ticks().every(Duration.ofSeconds(config.sseHeartbeatSeconds()))
        .onItem().transform(tick -> hub.heartbeat());
    Multi<OutboundSseEvent> live = Multi.createBy().merging().streams(client.processor(), heartbeats);

    return Multi.createBy().concatenating().streams(Multi.createFrom().iterable(initial), live)
        .onTermination().invoke(() -> hub.unregister(client))
        .onCancellation().invoke(() -> hub.unregister(client));
  }

  private void validateScope(Caller caller, String scopeType, Map<String, String> q) {
    if ("payment".equals(scopeType)) {
      boolean onlyPaymentKeys = q.keySet().stream()
          .allMatch(k -> Set.of("scope", "paFiscalCode", "noticeNumber").contains(k));
      if (q.get("paFiscalCode") == null || q.get("noticeNumber") == null || !onlyPaymentKeys) {
        throw Errors.invalidRequest();
      }
      if (!caller.allows(q.get("paFiscalCode"))) {
        throw Errors.notFound("Pagamento non disponibile.");
      }
      return;
    }
    if ("creditor".equals(scopeType) || "station".equals(scopeType)) {
      boolean stationShape = "station".equals(scopeType) ? q.get("stazione") != null : q.get("stazione") == null;
      if (q.get("paFiscalCode") == null || q.get("noticeNumber") != null || !stationShape) {
        throw Errors.invalidRequest();
      }
      if (!service.creditorVisible(caller, q.get("paFiscalCode"))) {
        throw Errors.notFound("Ente non disponibile.");
      }
    }
  }

  private static Set<String> eventParams() {
    Set<String> set = new HashSet<>(QuerySupport.FILTERS);
    set.addAll(Set.of("scope", "from", "to", "live"));
    set.add("keys");
    return Set.copyOf(set);
  }
}
