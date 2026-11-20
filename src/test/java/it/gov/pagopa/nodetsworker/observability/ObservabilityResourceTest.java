package it.gov.pagopa.nodetsworker.observability;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.Fixtures;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import it.gov.pagopa.nodetsworker.observability.sse.HistoryEntry;
import it.gov.pagopa.nodetsworker.observability.sse.SseHub;
import it.gov.pagopa.nodetsworker.observability.service.Caller;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityService;
import it.gov.pagopa.nodetsworker.observability.service.ProjectionMapper;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Boots the app with both datasources and exercises the observability read API against seeded fixtures. */
@QuarkusTest
class ObservabilityResourceTest {

  @TestHTTPResource("/")
  URI baseUri;

  @Inject
  ProjectionRepository repository;

  @Inject
  ObservabilityService service;

  @Inject
  SseHub hub;

  @BeforeEach
  void seed() {
    if (repository.countPayments() == 0) {
      for (DomainPayment payment : Fixtures.build(Instant.now())) {
        repository.persist(ProjectionMapper.toEntity(payment));
      }
      repository.meta();
    }
  }

  @Test
  void me() {
    given().when().get("/api/me").then().statusCode(200)
        .body("id", equalTo("demo-operator"))
        .body("role", equalTo("operator"))
        .body("authentication", equalTo("LOCAL_DEMO_ONLY"))
        .body("capabilities.revealToken", equalTo(true));
  }

  @Test
  void catalog() {
    given().when().get("/api/catalog").then().statusCode(200)
        .body("logicalStates", hasItem("INSERTED"))
        .body("attemptStates", hasItem("PAYMENT_UNKNOWN"))
        .body("unknownFilterValue", equalTo("~unknown"))
        .body("psps", hasItem("ABI00001"));
  }

  @Test
  void liveFlagIsAcceptedOnlyOnTheOverviewWindow() {
    given().when().get("/api/overview?live=true").then().statusCode(200)
        .body("primitives", org.hamcrest.Matchers.notNullValue());
    given().when().get("/api/overview?live=false").then().statusCode(200);
    given().when().get("/api/overview?live=maybe").then().statusCode(400);
    given().when().get("/api/creditors?live=true").then().statusCode(400);
    given().when().get("/api/events?scope=creditor&paFiscalCode=00000000001&live=true").then().statusCode(400);
  }

  @Test
  void primitiveWarningsRequireThePrimitiveAndKnownParametersOnly() {
    given().when().get("/api/overview/primitive-warnings?primitive=pspNotifyPayment").then().statusCode(200)
        .body("items.size()", equalTo(0))
        .body("nextCursor", org.hamcrest.Matchers.nullValue())
        .body("snapshotRevision", notNullValue());
    given().when().get("/api/overview/primitive-warnings").then().statusCode(400);
    given().when().get("/api/overview/primitive-warnings?primitive=" + "x".repeat(129)).then().statusCode(400);
    given().when().get("/api/overview/primitive-warnings?primitive=a&sort=key").then().statusCode(400);
    given().when().get("/api/overview/primitive-warnings?primitive=a&live=maybe").then().statusCode(400);
    given().when().get("/api/overview/primitive-warnings?primitive=a&live=true&psp=ABI00001&serviceIdentifier=~unknown")
        .then().statusCode(200);
    given().when().get("/api/overview/primitive-warnings?primitive=a&cursor=" + "z".repeat(32)).then().statusCode(410);
  }

  @Test
  void paymentsList() {
    given().when().get("/api/payments").then().statusCode(200)
        .body("items.size()", equalTo(8))
        .body("snapshotRevision", notNullValue());
  }

  @Test
  void paymentsFilterUnresolved() {
    given().when().get("/api/payments?unresolvedOnly=true").then().statusCode(200)
        .body("items.paFiscalCode", hasItem("00000000001"));
  }

  @Test
  void paymentDetailMasksToken() {
    given().when().get("/api/payments/00000000001/300000000000000001").then().statusCode(200)
        .body("state", equalTo("PAYING"))
        .body("attempts[0].tokenDisplay", notNullValue())
        .body("attempts[0].state", equalTo("PAYMENT_UNKNOWN"))
        .body(not(containsString("SYNTHETIC-TOKEN")));
  }

  @Test
  void paymentDetailNotFound() {
    given().when().get("/api/payments/00000000009/300000000000000099").then().statusCode(404)
        .body("code", equalTo("NOT_FOUND"));
  }

  @Test
  void overview() {
    // The default window is the last hour: only entities with activity in it are counted.
    given().when().get("/api/overview").then().statusCode(200)
        .body("counts.payments", equalTo(0))
        .body("window", notNullValue())
        .body("freshness.status", equalTo("CURRENT"));
    given().queryParam("from", IsoTime.format(Instant.now().minus(60, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS)))
        .queryParam("to", IsoTime.format(Instant.now().plus(1, ChronoUnit.HOURS)))
        .when().get("/api/overview").then().statusCode(200)
        // Seven of the eight demo payments are dated; the fully undated one matches no interval.
        .body("counts.payments", equalTo(7))
        .body("counts.attempts", equalTo(8))
        .body("unresolved.attempts", equalTo(2));
  }

  @Test
  void overviewCountsFollowTheWindowAndMatchTheLists() {
    String from = IsoTime.format(Instant.now().minus(2, ChronoUnit.DAYS).minus(12, ChronoUnit.HOURS));
    String to = IsoTime.format(Instant.now().plus(1, ChronoUnit.HOURS));
    int attempts = given().queryParam("from", from).queryParam("to", to)
        .when().get("/api/overview").then().statusCode(200).extract().path("counts.attempts");
    int payments = given().queryParam("from", from).queryParam("to", to)
        .when().get("/api/overview").then().statusCode(200).extract().path("counts.payments");
    assertTrue(attempts > 0 && attempts < 8);
    given().queryParam("from", from).queryParam("to", to).queryParam("pageSize", 100)
        .when().get("/api/attempts").then().statusCode(200).body("items.size()", equalTo(attempts));
    given().queryParam("from", from).queryParam("to", to).queryParam("pageSize", 100)
        .when().get("/api/payments").then().statusCode(200).body("items.size()", equalTo(payments));
  }

  @Test
  void listsAcceptAnOptionalWindowWithBothBoundsAndLiveOnlyWithAnInterval() {
    String now = IsoTime.format(Instant.now());
    given().queryParam("from", now).when().get("/api/payments").then().statusCode(400);
    given().queryParam("to", now).when().get("/api/attempts").then().statusCode(400);
    given().queryParam("live", "true").when().get("/api/payments").then().statusCode(400);
    given().queryParam("from", IsoTime.format(Instant.now().minus(1, ChronoUnit.HOURS))).queryParam("to", now)
        .queryParam("live", "true").when().get("/api/payments").then().statusCode(200);
    given().when().get("/api/creditors?live=true").then().statusCode(400);
  }

  @Test
  void overviewEventsStartsAnSseStream() throws Exception {
    Instant to = Instant.now();
    Instant from = to.minus(1, ChronoUnit.HOURS);
    URI uri = baseUri.resolve("/api/events?scope=overview&from=" + IsoTime.format(from)
        + "&to=" + IsoTime.format(to));
    HttpRequest request = HttpRequest.newBuilder(uri).header("Accept", "text/event-stream").GET().build();

    HttpResponse<InputStream> response = HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.ofInputStream());
    try (InputStream body = response.body();
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      String firstEvent = reader.lines().limit(3).collect(Collectors.joining("\n"));

      assertEquals(200, response.statusCode());
      assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));
      assertTrue(firstEvent.contains("event:ready"), firstEvent);
    }
  }

  @Test
  void paymentListEventsStartsAnSseStreamForVisibleKey() throws Exception {
    SseResponse response = readSse("/api/events?scope=payment-list&keys=00000000001:300000000000000001");

    assertEquals(200, response.statusCode());
    assertTrue(response.contentType().startsWith("text/event-stream"));
    assertTrue(response.firstEvent().contains("event:ready"), response.firstEvent());
    assertTrue(response.firstEvent().contains("\"scope\":\"payment-list\""), response.firstEvent());
  }

  @Test
  void paymentListEventsCanonicalizesKeysAndUsesGlobalRevisionWithoutWindow() throws Exception {
    SseResponse response = readSse("/api/events?scope=payment-list"
        + "&keys=00000000001:300000000000000002,00000000001:300000000000000001");

    assertEquals(200, response.statusCode());
    assertTrue(response.firstEvent().contains(
        "\"keys\":\"00000000001:300000000000000001,00000000001:300000000000000002\""),
        response.firstEvent());
    assertTrue(response.firstEvent().contains("\"projectionRevision\":" + service.revision()),
        response.firstEvent());
    assertTrue(!response.firstEvent().contains("\"window\""), response.firstEvent());
  }

  @Test
  void paymentListStreamReplaysOnlySelectedPaymentDeltas() throws Exception {
    String initialPath = "/api/events?scope=payment-list&keys=00000000001:300000000000000002,"
        + "00000000001:300000000000000001";
    String replayPath = "/api/events?scope=payment-list&keys=00000000001:300000000000000001,"
        + "00000000001:300000000000000002";
    Map<String, String> scope = Map.of("scope", "payment-list",
        "keys", "00000000001:300000000000000001,00000000001:300000000000000002");
    ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
    try {
      String ready;
      try (SseConnection initial = openSse(initialPath, null, readerExecutor)) {
        assertEquals(200, initial.statusCode());
        assertTrue(initial.contentType().startsWith("text/event-stream"));
        ready = initial.readEvent();
        assertTrue(ready.contains("event:ready"), ready);
      }

      String readyId = ready.lines().filter(line -> line.startsWith("id:"))
          .map(line -> line.substring("id:".length())).findFirst().orElseThrow();
      long sequence = hub.resolveCursor(readyId, Caller.implicit(), scope).sequence() + 1;
      hub.publish(new HistoryEntry(sequence, sequence, Instant.now(), List.of(), List.of(), List.of(
          paymentChange("300000000000000002", "second"),
          paymentChange("300000000000000003", "unselected"),
          paymentChange("300000000000000001", "first")), null));

      try (SseConnection replay = openSse(replayPath, readyId, readerExecutor)) {
        String firstDelta = replay.readEvent();
        String secondDelta = replay.readEvent();
        String readyAfterReplay = replay.readEvent();

        assertTrue(firstDelta.contains("event:payment.delta"), firstDelta);
        assertTrue(firstDelta.contains("\"noticeNumber\":\"300000000000000001\""), firstDelta);
        assertTrue(!firstDelta.contains("unselected"), firstDelta);
        assertTrue(secondDelta.contains("event:payment.delta"), secondDelta);
        assertTrue(secondDelta.contains("\"noticeNumber\":\"300000000000000002\""), secondDelta);
        assertTrue(!secondDelta.contains("unselected"), secondDelta);
        assertTrue(readyAfterReplay.contains("event:ready"), readyAfterReplay);
      }
    } finally {
      readerExecutor.shutdownNow();
    }
  }

  @Test
  void singlePaymentStreamReplaysOnlyMatchingDeltasWithoutChangingReadyShape() throws Exception {
    String path = "/api/events?scope=payment&paFiscalCode=00000000001"
        + "&noticeNumber=300000000000000001";
    Map<String, String> scope = Map.of(
        "scope", "payment", "paFiscalCode", "00000000001", "noticeNumber", "300000000000000001");
    ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
    try {
      try (SseConnection initial = openSse(path, null, readerExecutor)) {
        assertEquals(200, initial.statusCode());
        String ready = initial.readEvent();
        assertTrue(ready.contains("event:ready"), ready);
        assertTrue(ready.contains("\"scope\":\"payment\""), ready);
        assertTrue(!ready.contains("\"window\""), ready);

        String readyId = ready.lines().filter(line -> line.startsWith("id:"))
            .map(line -> line.substring("id:".length())).findFirst().orElseThrow();
        long sequence = hub.resolveCursor(readyId, Caller.implicit(), scope).sequence() + 1;
        hub.publish(new HistoryEntry(sequence, sequence, Instant.now(), List.of(), List.of(), List.of(
            paymentChange("300000000000000001", "matching"),
            paymentChange("300000000000000002", "non-matching")), null));

        String matchingLiveDelta = initial.readEventSkippingHeartbeats();
        assertTrue(matchingLiveDelta.contains("event:payment.delta"), matchingLiveDelta);
        assertTrue(matchingLiveDelta.contains("\"noticeNumber\":\"300000000000000001\""), matchingLiveDelta);
        assertTrue(matchingLiveDelta.contains("\"value\":\"matching\""), matchingLiveDelta);
        assertTrue(!matchingLiveDelta.contains("non-matching"), matchingLiveDelta);

        try (SseConnection replay = openSse(path, readyId, readerExecutor)) {
          String matchingDelta = replay.readEvent();
          String readyAfterReplay = replay.readEvent();

          assertTrue(matchingDelta.contains("event:payment.delta"), matchingDelta);
          assertTrue(matchingDelta.contains("\"noticeNumber\":\"300000000000000001\""), matchingDelta);
          assertTrue(matchingDelta.contains("\"value\":\"matching\""), matchingDelta);
          assertTrue(!matchingDelta.contains("non-matching"), matchingDelta);
          assertTrue(readyAfterReplay.contains("event:ready"), readyAfterReplay);
        }
      }
    } finally {
      readerExecutor.shutdownNow();
    }
  }

  @Test
  void paymentListEventsRejectInvalidKeysAndParameters() {
    for (String query : new String[] {
        "scope=payment-list",
        "scope=payment-list&keys=invalid",
        "scope=payment-list&keys=00000000001:300000000000000001,00000000001:300000000000000001",
        "scope=payment-list&keys=00000000001:300000000000000001&keys=00000000001:300000000000000002",
        "scope=payment-list&keys=00000000001:300000000000000001&extra=value",
        "scope=payment&paFiscalCode=00000000001&noticeNumber=300000000000000001"
            + "&keys=00000000001:300000000000000001"
    }) {
      given().when().get("/api/events?" + query).then().statusCode(400)
          .body("code", equalTo("INVALID_REQUEST"));
    }
  }

  @Test
  void eventsRejectMissingScope() {
    given().when().get("/api/events?keys=00000000001:300000000000000001").then().statusCode(400)
        .body("code", equalTo("INVALID_REQUEST"));
  }

  @Test
  void paymentListEventsNotFoundForMissingPayment() {
    given().when().get("/api/events?scope=payment-list&keys=00000000009:300000000000000099")
        .then().statusCode(404)
        .body("code", equalTo("NOT_FOUND"));
  }

  private SseResponse readSse(String path) throws Exception {
    URI uri = baseUri.resolve(path);
    HttpRequest request = HttpRequest.newBuilder(uri).header("Accept", "text/event-stream").GET().build();
    HttpResponse<InputStream> response = HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.ofInputStream());
    try (InputStream body = response.body();
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      List<String> eventLines = new ArrayList<>();
      for (int line = 0; line < 8; line++) {
        String eventLine = reader.readLine();
        if (eventLine == null) {
          break;
        }
        eventLines.add(eventLine);
        if (eventLine.startsWith("data:")) {
          break;
        }
      }
      String firstEvent = String.join("\n", eventLines);
      return new SseResponse(response.statusCode(),
          response.headers().firstValue("Content-Type").orElse(""), firstEvent);
    }
  }

  private SseConnection openSse(String path, String lastEventId, ExecutorService readerExecutor)
      throws Exception {
    URI uri = baseUri.resolve(path);
    HttpRequest.Builder request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(5))
        .header("Accept", "text/event-stream");
    if (lastEventId != null) {
      request.header("Last-Event-ID", lastEventId);
    }
    HttpResponse<InputStream> response = HttpClient.newHttpClient()
        .send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
    BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
    return new SseConnection(response, response.body(), reader, readerExecutor);
  }

  private HistoryEntry.PaymentChange paymentChange(String noticeNumber, String value) {
    return new HistoryEntry.PaymentChange("00000000001", noticeNumber, service.revision(),
        Map.of("value", value), false);
  }

  private static final class SseConnection implements AutoCloseable {
    private final HttpResponse<InputStream> response;
    private final InputStream body;
    private final BufferedReader reader;
    private final ExecutorService readerExecutor;

    private SseConnection(HttpResponse<InputStream> response, InputStream body, BufferedReader reader,
        ExecutorService readerExecutor) {
      this.response = response;
      this.body = body;
      this.reader = reader;
      this.readerExecutor = readerExecutor;
    }

    private String readEvent() throws Exception {
      return readEvent(Duration.ofSeconds(5));
    }

    private String readEventSkippingHeartbeats() throws Exception {
      long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      while (true) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          throw new java.util.concurrent.TimeoutException("Timed out waiting for an SSE event");
        }
        String event = readEvent(Duration.ofNanos(remaining));
        if (!":heartbeat".equals(event)) {
          return event;
        }
      }
    }

    private String readEvent(Duration timeout) throws Exception {
      return readerExecutor.submit(() -> {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.isEmpty() && !lines.isEmpty()) {
            return String.join("\n", lines);
          }
          if (!line.isEmpty()) {
            lines.add(line);
            if (line.startsWith("data:")) {
              return String.join("\n", lines);
            }
          }
        }
        return String.join("\n", lines);
      }).get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public void close() throws Exception {
      reader.close();
      body.close();
    }

    private int statusCode() {
      return response.statusCode();
    }

    private String contentType() {
      return response.headers().firstValue("Content-Type").orElse("");
    }
  }

  private record SseResponse(int statusCode, String contentType, String firstEvent) {}

  @Test
  void overviewTooManyDimensions() {
    given().when().get("/api/overview?psp=ABI00001&canale=SYNTHETIC_CHANNEL_A").then().statusCode(422)
        .body("code", equalTo("UNSUPPORTED_AGGREGATION"))
        .body("fallback", equalTo("/api/attempts"));
  }

  @Test
  void creditorsAndStations() {
    given().when().get("/api/creditors").then().statusCode(200)
        .body("items.paFiscalCode", hasItem("00000000001"));
    given().when().get("/api/creditors/00000000001/stations").then().statusCode(200)
        .body("items", notNullValue());
  }

  @Test
  void revealAndLookup() {
    given().contentType("application/json").body("{}")
        .when().post("/api/attempts/att_001_1/reveal").then().statusCode(200)
        .body("attemptId", equalTo("att_001_1"))
        .body("paymentToken", equalTo("SYNTHETIC-TOKEN-1-1"));

    given().contentType("application/json").body("{\"paymentToken\":\"SYNTHETIC-TOKEN-1-1\"}")
        .when().post("/api/token-lookup").then().statusCode(200)
        .body("attemptId", equalTo("att_001_1"))
        .body("paFiscalCode", equalTo("00000000001"));
  }

  @Test
  void invalidQueryParam() {
    given().when().get("/api/payments?bogus=1").then().statusCode(400)
        .body("code", equalTo("INVALID_REQUEST"));
  }
}
