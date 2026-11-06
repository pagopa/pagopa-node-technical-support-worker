package it.gov.pagopa.nodetsworker.observability.sse;

import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import it.gov.pagopa.nodetsworker.observability.domain.Filters;
import it.gov.pagopa.nodetsworker.observability.dto.AggregateDto;
import it.gov.pagopa.nodetsworker.observability.service.Caller;
import it.gov.pagopa.nodetsworker.observability.service.QuerySupport;
import jakarta.ws.rs.sse.OutboundSseEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** One connected SSE subscriber: its scope/window, its live event channel and its last-sent state. */
public class SseClient {

  private final String clientId;
  private final Caller caller;
  private final String scopeType;
  private final Map<String, String> scope;
  private final List<QuerySupport.PaymentKey> paymentKeys;
  private final Instant from;
  private final Instant to;
  private final UnicastProcessor<OutboundSseEvent> processor = UnicastProcessor.create();

  /** Last aggregate snapshot sent to this client, used to diff the next aggregate delta. */
  private AggregateDto lastAggregate;
  /** Last event delivered, replayed verbatim by the {@code duplicate} scenario. */
  private OutboundSseEvent lastEvent;

  public SseClient(String clientId, Caller caller, Map<String, String> scope, Instant from, Instant to) {
    this.clientId = clientId;
    this.caller = caller;
    this.scope = scope;
    this.scopeType = scope.get("scope");
    this.paymentKeys = "payment-list".equals(scopeType)
        ? QuerySupport.parsePaymentKeys(scope.get("keys"))
        : List.of();
    this.from = from;
    this.to = to;
  }

  public String clientId() {
    return clientId;
  }

  public Caller caller() {
    return caller;
  }

  /** A rolling window: activity after the upper bound still counts (the client asked for {@code live=true}). */
  public boolean live() {
    return "true".equals(scope.get("live"));
  }

  public String scopeType() {
    return scopeType;
  }

  public Map<String, String> scope() {
    return scope;
  }

  public List<QuerySupport.PaymentKey> paymentKeys() {
    return paymentKeys;
  }

  public Instant from() {
    return from;
  }

  public Instant to() {
    return to;
  }

  public boolean isPayment() {
    return "payment".equals(scopeType);
  }

  public Filters filters() {
    return QuerySupport.filters(scope);
  }

  public UnicastProcessor<OutboundSseEvent> processor() {
    return processor;
  }

  public AggregateDto lastAggregate() {
    return lastAggregate;
  }

  public void setLastAggregate(AggregateDto lastAggregate) {
    this.lastAggregate = lastAggregate;
  }

  public OutboundSseEvent lastEvent() {
    return lastEvent;
  }

  public void setLastEvent(OutboundSseEvent lastEvent) {
    this.lastEvent = lastEvent;
  }

  public void emit(OutboundSseEvent event) {
    this.lastEvent = event;
    processor.onNext(event);
  }
}
