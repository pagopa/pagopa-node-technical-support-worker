package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.domain.DomainAttempt;
import it.gov.pagopa.nodetsworker.observability.domain.DomainPayment;
import it.gov.pagopa.nodetsworker.observability.domain.Filters;
import it.gov.pagopa.nodetsworker.observability.domain.PrimitiveLogic;
import it.gov.pagopa.nodetsworker.observability.domain.ProjectionLogic;
import it.gov.pagopa.nodetsworker.observability.dto.AggregateDto;
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
import java.util.LinkedHashMap;
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
        .authentication("LOCAL_DEMO_ONLY")
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
