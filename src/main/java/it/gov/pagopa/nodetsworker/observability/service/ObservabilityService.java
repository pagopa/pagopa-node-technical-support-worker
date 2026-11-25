package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.domain.DomainAttempt;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.Filters;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveLogic;
import it.gov.pagopa.nodetsworker.observability.domain.ProjectionLogic;
import it.gov.pagopa.nodetsworker.observability.dto.AggregateDto;
import it.gov.pagopa.nodetsworker.observability.dto.AnalyticsDto;
import it.gov.pagopa.nodetsworker.observability.dto.AttemptDto;
import it.gov.pagopa.nodetsworker.observability.dto.CatalogDto;
import it.gov.pagopa.nodetsworker.observability.dto.CreditorDetailDto;
import it.gov.pagopa.nodetsworker.observability.dto.CreditorRowDto;
import it.gov.pagopa.nodetsworker.observability.dto.MeDto;
import it.gov.pagopa.nodetsworker.observability.dto.OverviewDto;
import it.gov.pagopa.nodetsworker.observability.dto.PageDto;
import it.gov.pagopa.nodetsworker.observability.dto.PaymentDetailDto;
import it.gov.pagopa.nodetsworker.observability.dto.PaymentSummaryDto;
import it.gov.pagopa.nodetsworker.observability.dto.PrimitiveWarningDto;
import it.gov.pagopa.nodetsworker.observability.dto.RevealResponseDto;
import it.gov.pagopa.nodetsworker.observability.dto.StationRowDto;
import it.gov.pagopa.nodetsworker.observability.dto.TokenLookupResponseDto;
import it.gov.pagopa.nodetsworker.observability.dto.TrafficBucketDto;
import it.gov.pagopa.nodetsworker.observability.dto.WindowDto;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveBucket;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.model.AttemptState;
import it.gov.pagopa.nodetsworker.observability.model.LogicalState;
import it.gov.pagopa.nodetsworker.observability.repository.ProjectionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import it.gov.pagopa.nodetsworker.observability.ingestion.PaymentFsm;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Orchestrates the observability read API over the projection, mirroring {@code mocks/server.mjs}. */
@ApplicationScoped
public class ObservabilityService {

  @Inject
  ProjectionRepository repository;

  @Inject
  CursorService cursors;

  @Inject
  FreshnessService freshnessService;

  @Inject
  ObservabilityConfig config;

  // --- identity & catalog ---------------------------------------------------

  public MeDto me(Caller caller) {
    return MeDto.builder()
        .id(caller.id())
        .role(caller.role())
        .visiblePaFiscalCodes(caller.allowedPa() == null ? null : List.of(caller.allowedPa()))
        .capabilities(new it.gov.pagopa.nodetsworker.observability.dto.CapabilitiesDto(
            caller.canReadTokens(), caller.canReadTokens(), caller.canMock()))
        .authentication(caller.isImplicit() ? "LOCAL_DEMO_ONLY" : "ENTRA_ID")
        .build();
  }

  public it.gov.pagopa.nodetsworker.observability.dto.FreshnessDto freshnessCurrent() {
    return freshnessService.current();
  }

  public CatalogDto catalog(Caller caller) {
    String pa = caller.allowedPa();
    return CatalogDto.builder()
        .logicalStates(LogicalState.ORDERED.stream().map(Enum::name).toList())
        .attemptStates(AttemptState.ORDERED.stream().map(Enum::name).toList())
        .warningStates(AttemptState.WARNING)
        .unknownFilterValue(Filters.UNKNOWN)
        .serviceIdentifiers(sortedDistinct(repository.distinctDimension("serviceIdentifier", pa)))
        .psps(sortedDistinct(repository.distinctDimension("psp", pa)))
        .canali(sortedDistinct(repository.distinctDimension("canale", pa)))
        .stazioni(sortedDistinct(repository.distinctDimension("stazione", pa)))
        .aggregateFilterPolicy("ONE_ATTEMPT_DIMENSION;STATION_REQUIRES_PA")
        .build();
  }

  private static List<String> sortedDistinct(List<String> values) {
    TreeSet<String> set = new TreeSet<>(Comparator.comparing(String::valueOf));
    set.addAll(values);
    return new ArrayList<>(set);
  }

  // --- lists ----------------------------------------------------------------

  public PageDto<PaymentSummaryDto> payments(Caller caller, Map<String, String> q) {
    List<DomainPayment> visible = visible(caller);
    List<DomainPayment> selected = withinRequestedWindow(
        ProjectionLogic.selected(visible, QuerySupport.filters(q), null), q);
    Map<String, DomainPayment> byKey = index(visible);
    List<PaymentSummaryDto> items = new ArrayList<>();
    for (DomainPayment p : selected) {
      items.add(ProjectionMapper.toSummaryDto(byKey.get(p.key())));
    }
    items.sort(Comparator.comparing(s -> s.getPaFiscalCode() + "/" + s.getNoticeNumber()));
    return cursors.paginate(items, caller, "/api/payments", q, "key", revision(), config);
  }

  public PageDto<AttemptDto> attempts(Caller caller, Map<String, String> q) {
    List<DomainPayment> selected = withinRequestedWindow(
        ProjectionLogic.selected(visible(caller), QuerySupport.filters(q), null), q);
    List<AttemptDto> items = new ArrayList<>();
    for (DomainPayment p : selected) {
      for (DomainAttempt a : p.getAttempts()) {
        items.add(ProjectionMapper.toSafeAttemptDto(a, p));
      }
    }
    items.sort(Comparator.comparing(AttemptDto::getAttemptId));
    return cursors.paginate(items, caller, "/api/attempts", q, "key", revision(), config);
  }

  /**
   * Restricts a list to the entities with activity in the optional {@code [from, to)} interval (both or
   * none; {@code live} only with an interval), the same rule as the overview aggregates. Without an
   * interval the list covers the whole retention.
   */
  private List<DomainPayment> withinRequestedWindow(List<DomainPayment> payments, Map<String, String> q) {
    boolean hasFrom = q.containsKey("from");
    if (hasFrom != q.containsKey("to") || (!hasFrom && q.containsKey("live"))) {
      throw Errors.invalidRequest();
    }
    if (!hasFrom) {
      return payments;
    }
    Instant[] window = QuerySupport.window(q, Instant.now(), config);
    return ProjectionLogic.withinWindow(payments, window[0], window[1], QuerySupport.live(q));
  }

  public PaymentDetailDto paymentDetail(Caller caller, String paFiscalCode, String noticeNumber) {
    return ProjectionMapper.toDetailDto(getPayment(caller, paFiscalCode, noticeNumber));
  }

  // --- aggregates -----------------------------------------------------------

  public OverviewDto overview(Caller caller, Map<String, String> q) {
    Instant[] window = QuerySupport.window(q, Instant.now(), config);
    List<DomainPayment> filtered = ProjectionLogic.selected(visible(caller), QuerySupport.filters(q), null);
    boolean live = QuerySupport.live(q);
    AggregateDto aggregate = AggregationService.aggregate(filtered, window[0], window[1], Instant.now(),
        revision(), live);
    OverviewDto dto = new OverviewDto();
    copyAggregate(aggregate, dto);
    dto.setFreshness(freshnessService.current());
    // A live window has no upper bound; one day ahead covers every request still to be recorded.
    Instant primitivesTo = live ? Instant.now().plus(1, ChronoUnit.DAYS) : window[1];
    dto.setPrimitives(PrimitiveLogic.rows(repository.primitiveBuckets(window[0], primitivesTo),
        QuerySupport.filters(q), window[0], window[1], caller.allowedPa(), live));
    return dto;
  }

  static final String ACTIVATE = "activatePaymentNotice";
  static final String ACTIVATE_V2 = "activatePaymentNoticeV2";
  static final String OUTCOME = "sendPaymentOutcome";
  static final String OUTCOME_V2 = "sendPaymentOutcomeV2";

  /**
   * Activations with outcome OK versus {@code sendPaymentOutcome} requests on the same minute-aligned
   * window and dimension filters. An activation is OK when the Nodo issued its payment token: the RE
   * {@code payment_PAYING} event with {@code tipoEvento} activate V1/V2 (the interface {@code esito} is only
   * technical and is not used). Outcomes count every V1/V2 request (OK or KO) from the primitive counters.
   * Activated tokens with no transition triggered by {@code sendPaymentOutcome}/V2 (no accepted outcome) are
   * broken down per token by current state and {@code mod3Cancel}/V2 cause. The
   * counters carry no creditor, so a caller restricted to one creditor is refused.
   */
  public AnalyticsDto activationsOutcomes(Caller caller, Map<String, String> q) {
    if (caller.allowedPa() != null) {
      throw Errors.forbidden("Analytics non disponibili per un ente creditore.");
    }
    Instant now = Instant.now();
    Instant[] window = QuerySupport.window(q, now, config);
    boolean live = QuerySupport.live(q);
    Instant from = PrimitiveLogic.bucketOf(window[0]);
    Instant to = live ? null : ceilMinute(window[1]);
    Filters filters = QuerySupport.filters(q);

    long activate = 0;
    long activateV2 = 0;
    for (var group : repository.activationGroups(List.of(ACTIVATE, ACTIVATE_V2), from, to)) {
      if (PrimitiveLogic.matchesDimensions(group.serviceIdentifier(), group.psp(), group.canale(),
          group.stazione(), filters)) {
        if (ACTIVATE.equals(group.trigger())) {
          activate += group.attempts();
        } else {
          activateV2 += group.attempts();
        }
      }
    }
    boolean retained = !from.isBefore(now.minus(config.retentionDays(), ChronoUnit.DAYS));
    Instant lastObserved = repository.lastObservedAttemptEvent();
    // No reliable source watermark is recorded: completeness cannot be proven, so it is never asserted.
    var coverage = retained
        ? new AnalyticsDto.CoverageDto("UNKNOWN", List.of("NO_INGESTION_WATERMARK"), IsoTime.format(lastObserved))
        : new AnalyticsDto.CoverageDto("UNAVAILABLE", List.of("RETENTION", "NO_INGESTION_WATERMARK"),
            IsoTime.format(lastObserved));
    AnalyticsDto.TokensDto tokens = retained
        ? tokenBreakdown(repository.tokenGroups(List.of(ACTIVATE, ACTIVATE_V2), OUTCOME, OUTCOME_V2, from, to),
            filters)
        : null;
    long outcome = 0;
    long outcomeV2 = 0;
    // A live window has no upper bound; one day ahead covers every request still to be recorded.
    for (PrimitiveBucket bucket : repository.primitiveBuckets(from, to == null ? now.plus(1, ChronoUnit.DAYS) : to)) {
      if (!PrimitiveLogic.matchesDimensions(bucket.serviceIdentifier(), bucket.psp(), bucket.canale(),
          bucket.stazione(), filters)) {
        continue;
      }
      if (OUTCOME.equals(bucket.primitive())) {
        outcome += bucket.requests();
      } else if (OUTCOME_V2.equals(bucket.primitive())) {
        outcomeV2 += bucket.requests();
      }
    }
    long activations = activate + activateV2;
    long outcomes = outcome + outcomeV2;
    return new AnalyticsDto(
        new WindowDto(IsoTime.format(from), to == null ? null : IsoTime.format(to)), live,
        new AnalyticsDto.ActivationsDto(activations, activate, activateV2),
        new AnalyticsDto.OutcomesDto(outcomes, outcome, outcomeV2),
        tokens, coverage, outcomes - activations, freshnessService.current(), IsoTime.format(now));
  }

  static AnalyticsDto.TokensDto tokenBreakdown(List<ProjectionRepository.TokenGroup> groups,
      Filters filters) {
    long accepted = 0;
    long acceptedV2 = 0;
    long mod3 = 0;
    long mod3V2 = 0;
    Map<String, Long> cancelledByCause = new HashMap<>();
    Map<String, Long> nonTerminal = new HashMap<>();
    Map<List<String>, Long> otherTerminal = new HashMap<>();
    for (var g : groups) {
      if (!PrimitiveLogic.matchesDimensions(g.serviceIdentifier(), g.psp(), g.canale(), g.stazione(), filters)) {
        continue;
      }
      String cause = g.cause() == null ? null : g.cause().toLowerCase(java.util.Locale.ROOT);
      if (g.outcomeVersion() == 2) {
        acceptedV2 += g.attempts();
      } else if (g.outcomeVersion() == 1) {
        accepted += g.attempts();
      } else if ("CANCELLED".equals(g.currentState()) && cause != null && cause.startsWith("mod3cancel")) {
        if (cause.endsWith("v2")) {
          mod3V2 += g.attempts();
        } else {
          mod3 += g.attempts();
        }
      } else if ("CANCELLED".equals(g.currentState())) {
        cancelledByCause.merge(Objects.toString(g.cause(), NO_CAUSE), g.attempts(), Long::sum);
      } else if (PaymentFsm.isTerminal(it.gov.pagopa.nodetsworker.observability.ingestion.AdmissionFilter.Entity.PAYMENT, g.currentState())) {
        otherTerminal.merge(java.util.Arrays.asList(g.currentState(), g.cause()), g.attempts(), Long::sum);
      } else {
        nonTerminal.merge(g.currentState(), g.attempts(), Long::sum);
      }
    }
    var byCause = cancelledByCause.entrySet().stream()
        .map(e -> new AnalyticsDto.Bucket(null, NO_CAUSE.equals(e.getKey()) ? null : e.getKey(), e.getValue()))
        .toList();
    var byState = nonTerminal.entrySet().stream()
        .map(e -> new AnalyticsDto.StateCount(e.getKey(), e.getValue()))
        .sorted(java.util.Comparator.comparingLong(AnalyticsDto.StateCount::getCount).reversed()
            .thenComparing(AnalyticsDto.StateCount::getState))
        .toList();
    var terminal = buckets(otherTerminal);
    var cancelled = new AnalyticsDto.ByCauseDto(sum(byCause), sorted(byCause));
    var open = new AnalyticsDto.NonTerminalDto(byState.stream().mapToLong(AnalyticsDto.StateCount::getCount).sum(),
        byState);
    var otherFinal = new AnalyticsDto.ByStateDto(sum(terminal), sorted(terminal));
    long total = accepted + acceptedV2 + mod3 + mod3V2 + cancelled.getTotal() + open.getTotal()
        + otherFinal.getTotal();
    return new AnalyticsDto.TokensDto(total, new AnalyticsDto.OutcomesDto(accepted + acceptedV2, accepted, acceptedV2),
        new AnalyticsDto.CancellationsDto(mod3 + mod3V2, mod3, mod3V2), cancelled, open, otherFinal);
  }

  /** Map key for a cause not recorded (a tipoEvento never contains a NUL character). */
  private static final String NO_CAUSE = "\0";

  private static List<AnalyticsDto.Bucket> buckets(Map<List<String>, Long> byStateAndCause) {
    return byStateAndCause.entrySet().stream()
        .map(e -> new AnalyticsDto.Bucket(e.getKey().get(0), e.getKey().get(1), e.getValue())).toList();
  }

  private static long sum(List<AnalyticsDto.Bucket> buckets) {
    return buckets.stream().mapToLong(AnalyticsDto.Bucket::getCount).sum();
  }

  private static List<AnalyticsDto.Bucket> sorted(List<AnalyticsDto.Bucket> buckets) {
    return buckets.stream().sorted(java.util.Comparator.comparingLong(AnalyticsDto.Bucket::getCount).reversed()
        .thenComparing(b -> Objects.toString(b.getState(), "") + Objects.toString(b.getCause(), ""))).toList();
  }

  private static Instant ceilMinute(Instant instant) {
    Instant floor = PrimitiveLogic.bucketOf(instant);
    return floor.equals(instant) ? floor : floor.plus(1, ChronoUnit.MINUTES);
  }

  /**
   * The primitive calls behind a {@code warnings} count of the overview (same window and dimension
   * filters), newest first and cursor-paginated.
   */
  public PageDto<PrimitiveWarningDto> primitiveWarnings(Caller caller, Map<String, String> q) {
    Instant[] window = QuerySupport.window(q, Instant.now(), config);
    boolean live = QuerySupport.live(q);
    Instant to = live ? Instant.now().plus(1, ChronoUnit.DAYS) : window[1];
    List<PrimitiveWarningDto> items = PrimitiveLogic.warnings(
        repository.primitiveWarnings(q.get("primitive"), window[0], to), q.get("primitive"),
        QuerySupport.filters(q), window[0], window[1], caller.allowedPa(), live);
    return cursors.paginate(items, caller, "/api/overview/primitive-warnings", q, "key", revision(), config);
  }

  public CreditorDetailDto creditorDetail(Caller caller, String paFiscalCode, Map<String, String> q) {
    ensureCreditorVisible(caller, paFiscalCode);
    Instant[] window = QuerySupport.window(q, Instant.now(), config);
    Map<String, String> scoped = new LinkedHashMap<>(q);
    scoped.put("paFiscalCode", paFiscalCode);
    List<DomainPayment> filtered = ProjectionLogic.selected(visible(caller), QuerySupport.filters(scoped), null);
    AggregateDto aggregate = AggregationService.aggregate(filtered, window[0], window[1], Instant.now(), revision(), false);
    CreditorDetailDto dto = new CreditorDetailDto();
    copyAggregate(aggregate, dto);
    dto.setFreshness(freshnessService.current());
    dto.setPaFiscalCode(paFiscalCode);
    return dto;
  }

  public PageDto<CreditorRowDto> creditors(Caller caller, Map<String, String> q, String sort) {
    Instant[] window = QuerySupport.window(q, Instant.now(), config);
    List<DomainPayment> visible = visible(caller);
    List<DomainPayment> filtered = ProjectionLogic.selected(visible, QuerySupport.filters(q), null);
    TreeSet<String> pas = new TreeSet<>();
    for (DomainPayment p : filtered) {
      pas.add(p.getPaFiscalCode());
    }
    List<CreditorRowDto> rows = new ArrayList<>();
    for (String pa : pas) {
      Map<String, String> scoped = new LinkedHashMap<>(q);
      scoped.put("paFiscalCode", pa);
      List<DomainPayment> scopedPayments = ProjectionLogic.selected(visible, QuerySupport.filters(scoped), null);
      AggregateDto metrics = AggregationService.aggregate(scopedPayments, window[0], window[1], Instant.now(), revision(), false);
      rows.add(CreditorRowDto.builder()
          .paFiscalCode(pa)
          .startedAttempts(sumStarted(metrics.getTraffic()))
          .counts(metrics.getCounts())
          .logicalStates(metrics.getLogicalStates())
          .unresolved(metrics.getUnresolved())
          .build());
    }
    boolean byUnresolved = "unresolved".equals(sort);
    rows.sort((a, b) -> {
      long diff = byUnresolved
          ? b.getUnresolved().getAttempts() - a.getUnresolved().getAttempts()
          : b.getStartedAttempts() - a.getStartedAttempts();
      return diff != 0 ? (int) Long.signum(diff) : a.getPaFiscalCode().compareTo(b.getPaFiscalCode());
    });
    return cursors.paginate(rows, caller, "/api/creditors", q, sort, revision(), config);
  }

  public PageDto<StationRowDto> stations(Caller caller, String paFiscalCode, Map<String, String> q) {
    ensureCreditorVisible(caller, paFiscalCode);
    Instant[] window = QuerySupport.window(q, Instant.now(), config);
    Map<String, String> scoped = new LinkedHashMap<>(q);
    scoped.put("paFiscalCode", paFiscalCode);
    List<DomainPayment> data = ProjectionLogic.selected(visible(caller), QuerySupport.filters(scoped), null);
    TreeSet<String> stazioni = new TreeSet<>(Comparator.comparing(String::valueOf));
    for (DomainPayment p : data) {
      for (DomainAttempt a : p.getAttempts()) {
        stazioni.add(a.getStazione());
      }
    }
    List<StationRowDto> rows = new ArrayList<>();
    for (String stazione : stazioni) {
      Filters stationFilter = Filters.builder().stazione(stazione == null ? Filters.UNKNOWN : stazione).build();
      List<DomainPayment> projection = ProjectionLogic.selected(data, stationFilter, null);
      AggregateDto metrics = AggregationService.aggregate(projection, window[0], window[1], Instant.now(), revision(), false);
      rows.add(StationRowDto.builder()
          .stazione(stazione)
          .traffic(metrics.getTraffic())
          .startedAttempts(sumStarted(metrics.getTraffic()))
          .logicalStates(metrics.getLogicalStates())
          .attemptStates(metrics.getAttemptStates())
          .unresolved(metrics.getUnresolved())
          .build());
    }
    return cursors.paginate(rows, caller, "/api/creditors/" + paFiscalCode + "/stations", q, "key", revision(), config);
  }

  // --- token operations -----------------------------------------------------

  public TokenLookupResponseDto tokenLookup(Caller caller, String paymentToken,
      String paFiscalCode, String noticeNumber) {
    DomainPayment payment = null;
    DomainAttempt attempt = null;
    for (DomainPayment p : visible(caller)) {
      // Optional PA/NAV are additional constraints, not an alternative key (§4.1).
      if ((paFiscalCode != null && !paFiscalCode.equals(p.getPaFiscalCode()))
          || (noticeNumber != null && !noticeNumber.equals(p.getNoticeNumber()))) {
        continue;
      }
      for (DomainAttempt a : p.getAttempts()) {
        if (paymentToken.equals(a.getPaymentToken())) {
          payment = p;
          attempt = a;
          break;
        }
      }
      if (payment != null) {
        break;
      }
    }
    return new TokenLookupResponseDto(
        payment == null ? null : payment.getPaFiscalCode(),
        payment == null ? null : payment.getNoticeNumber(),
        attempt == null ? null : attempt.getAttemptId(),
        payment == null ? 0 : payment.getProjectionRevision());
  }

  public RevealResponseDto reveal(Caller caller, String attemptId) {
    DomainAttempt attempt = getAttempt(caller, attemptId);
    return new RevealResponseDto(attempt.getAttemptId(), attempt.getPaymentToken());
  }

  // --- SSE visibility helpers ----------------------------------------------

  public boolean creditorVisible(Caller caller, String paFiscalCode) {
    return caller.allows(paFiscalCode)
        && visible(caller).stream().anyMatch(p -> p.getPaFiscalCode().equals(paFiscalCode));
  }

  /** The current revision of a visible payment, or {@code null} when it is absent/not visible. */
  public Long paymentRevisionIfVisible(Caller caller, String paFiscalCode, String noticeNumber) {
    for (DomainPayment p : visible(caller)) {
      if (p.getPaFiscalCode().equals(paFiscalCode) && p.getNoticeNumber().equals(noticeNumber)) {
        return p.getProjectionRevision();
      }
    }
    return null;
  }

  // --- helpers --------------------------------------------------------------

  public long revision() {
    return repository.aggregateRevision();
  }

  private List<DomainPayment> visible(Caller caller) {
    return repository.loadVisible(caller.allowedPa());
  }

  private static Map<String, DomainPayment> index(List<DomainPayment> payments) {
    Map<String, DomainPayment> byKey = new LinkedHashMap<>();
    for (DomainPayment p : payments) {
      byKey.put(p.key(), p);
    }
    return byKey;
  }

  private DomainPayment getPayment(Caller caller, String paFiscalCode, String noticeNumber) {
    for (DomainPayment p : visible(caller)) {
      if (p.getPaFiscalCode().equals(paFiscalCode) && p.getNoticeNumber().equals(noticeNumber)) {
        return p;
      }
    }
    throw Errors.notFound("Pagamento non disponibile.");
  }

  private DomainAttempt getAttempt(Caller caller, String attemptId) {
    for (DomainPayment p : visible(caller)) {
      for (DomainAttempt a : p.getAttempts()) {
        if (a.getAttemptId().equals(attemptId)) {
          return a;
        }
      }
    }
    throw Errors.notFound("Tentativo non disponibile.");
  }

  private void ensureCreditorVisible(Caller caller, String paFiscalCode) {
    if (!caller.allows(paFiscalCode) || visible(caller).stream().noneMatch(p -> p.getPaFiscalCode().equals(paFiscalCode))) {
      throw Errors.notFound("Ente non disponibile.");
    }
  }

  private static long sumStarted(List<TrafficBucketDto> traffic) {
    return traffic.stream().mapToLong(TrafficBucketDto::getStartedAttempts).sum();
  }

  private static void copyAggregate(AggregateDto from, AggregateDto to) {
    to.setSnapshotAt(from.getSnapshotAt());
    to.setProjectionRevision(from.getProjectionRevision());
    to.setWindow(from.getWindow());
    to.setCounts(from.getCounts());
    to.setLogicalStates(from.getLogicalStates());
    to.setAttemptStates(from.getAttemptStates());
    to.setRecipientStates(from.getRecipientStates());
    to.setUnresolved(from.getUnresolved());
    to.setMatrices(from.getMatrices());
    to.setTraffic(from.getTraffic());
    to.setTransitions(from.getTransitions());
    to.setDurations(from.getDurations());
    to.setAnomalies(from.getAnomalies());
  }
}
