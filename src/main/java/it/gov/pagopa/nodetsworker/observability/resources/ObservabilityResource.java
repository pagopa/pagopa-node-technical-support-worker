package it.gov.pagopa.nodetsworker.observability.resources;

import it.gov.pagopa.nodetsworker.observability.dto.AttemptDto;
import it.gov.pagopa.nodetsworker.observability.dto.CatalogDto;
import it.gov.pagopa.nodetsworker.observability.dto.CreditorDetailDto;
import it.gov.pagopa.nodetsworker.observability.dto.CreditorRowDto;
import it.gov.pagopa.nodetsworker.observability.dto.FreshnessDto;
import it.gov.pagopa.nodetsworker.observability.dto.MeDto;
import it.gov.pagopa.nodetsworker.observability.dto.OverviewDto;
import it.gov.pagopa.nodetsworker.observability.dto.PageDto;
import it.gov.pagopa.nodetsworker.observability.dto.PaymentDetailDto;
import it.gov.pagopa.nodetsworker.observability.dto.PaymentSummaryDto;
import it.gov.pagopa.nodetsworker.observability.dto.PrimitiveWarningDto;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import it.gov.pagopa.nodetsworker.observability.service.CallerResolver;
import it.gov.pagopa.nodetsworker.observability.service.ObservabilityService;
import it.gov.pagopa.nodetsworker.observability.service.QuerySupport;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** The observability read API ({@code /api/*}), implementing the functional contract in {@code mocks/}. */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@NoStore
public class ObservabilityResource {

  // Lists accept an optional [from, to) interval (both or none) with the same semantics as the overview.
  private static final Set<String> LIST_PARAMS = withExtra("pageSize", "cursor", "sort", "from", "to", "live");
  private static final Set<String> WINDOW_PARAMS = withExtra("from", "to");
  private static final Set<String> OVERVIEW_PARAMS = withExtra("from", "to", "live");
  private static final Set<String> PRIMITIVE_WARNING_PARAMS =
      withExtra("from", "to", "live", "primitive", "pageSize", "cursor");
  private static final Set<String> CREDITOR_PARAMS = withExtra("from", "to", "pageSize", "cursor", "sort");

  @Inject
  ObservabilityService service;

  @Inject
  CallerResolver callerResolver;

  @ConfigProperty(name = "snapshot.online.nodes.online-ndp001.service-identifier")
  String ndp001Identifier;

  @ConfigProperty(name = "snapshot.online.nodes.online-ndp004.service-identifier")
  String ndp004Identifier;

  @GET
  @Path("/me")
  public MeDto me(@Context UriInfo uriInfo) {
    requireNoQuery(uriInfo);
    return service.me(callerResolver.resolve());
  }

  @GET
  @Path("/catalog")
  public CatalogDto catalog(@Context UriInfo uriInfo) {
    requireNoQuery(uriInfo);
    return service.catalog(callerResolver.resolve());
  }

  @GET
  @Path("/freshness")
  public FreshnessDto freshness(@Context UriInfo uriInfo) {
    requireNoQuery(uriInfo);
    return service.freshnessCurrent();
  }

  @GET
  @Path("/serviceIdentifiers")
  public List<String> serviceIdentifiers(@Context UriInfo uriInfo) {
    requireNoQuery(uriInfo);
    return List.of(ndp001Identifier, ndp004Identifier);
  }

  @GET
  @Path("/payments")
  public PageDto<PaymentSummaryDto> payments(@Context UriInfo uriInfo) {
    Map<String, String> q = QuerySupport.validate(query(uriInfo), LIST_PARAMS);
    return service.payments(callerResolver.resolve(), q);
  }

  @GET
  @Path("/attempts")
  public PageDto<AttemptDto> attempts(@Context UriInfo uriInfo) {
    Map<String, String> q = QuerySupport.validate(query(uriInfo), LIST_PARAMS);
    return service.attempts(callerResolver.resolve(), q);
  }

  @GET
  @Path("/payments/{paFiscalCode}/{noticeNumber}")
  public PaymentDetailDto paymentDetail(@PathParam("paFiscalCode") String paFiscalCode,
      @PathParam("noticeNumber") String noticeNumber, @Context UriInfo uriInfo) {
    requireNoQuery(uriInfo);
    if (!paFiscalCode.matches("\\d{11}") || !noticeNumber.matches("\\d{18}")) {
      throw Errors.notFound("Pagamento non disponibile.");
    }
    return service.paymentDetail(callerResolver.resolve(), paFiscalCode, noticeNumber);
  }

  @GET
  @Path("/overview")
  public OverviewDto overview(@Context UriInfo uriInfo) {
    Map<String, String> q = QuerySupport.validate(query(uriInfo), OVERVIEW_PARAMS);
    QuerySupport.checkAggregate(q);
    return service.overview(callerResolver.resolve(), q);
  }

  /** Calls counted in {@code Overview.primitives[].warnings}: PSP, channel, creditor, station and time. */
  @GET
  @Path("/overview/primitive-warnings")
  public PageDto<PrimitiveWarningDto> primitiveWarnings(@Context UriInfo uriInfo) {
    Map<String, String> q = QuerySupport.validate(query(uriInfo), PRIMITIVE_WARNING_PARAMS);
    if (!q.containsKey("primitive") || q.get("primitive").length() > 128) {
      throw Errors.invalidRequest();
    }
    return service.primitiveWarnings(callerResolver.resolve(), q);
  }

  @GET
  @Path("/creditors")
  public PageDto<CreditorRowDto> creditors(@Context UriInfo uriInfo) {
    Map<String, String> q = QuerySupport.validate(query(uriInfo), CREDITOR_PARAMS);
    QuerySupport.checkAggregate(q);
    String sort = q.getOrDefault("sort", "traffic");
    if (!Set.of("traffic", "unresolved").contains(sort)) {
      throw Errors.invalidRequest();
    }
    return service.creditors(callerResolver.resolve(), q, sort);
  }

  @GET
  @Path("/creditors/{paFiscalCode}")
  public CreditorDetailDto creditorDetail(@PathParam("paFiscalCode") String paFiscalCode,
      @Context UriInfo uriInfo) {
    Map<String, String> q = QuerySupport.validate(query(uriInfo), CREDITOR_PARAMS);
    requireCreditorPath(q, paFiscalCode);
    if (q.containsKey("cursor") || q.containsKey("pageSize") || q.containsKey("sort")) {
      throw Errors.invalidRequest();
    }
    QuerySupport.checkAggregate(q);
    return service.creditorDetail(callerResolver.resolve(), paFiscalCode, q);
  }

  @GET
  @Path("/creditors/{paFiscalCode}/stations")
  public PageDto<?> stations(@PathParam("paFiscalCode") String paFiscalCode, @Context UriInfo uriInfo) {
    Map<String, String> q = QuerySupport.validate(query(uriInfo), CREDITOR_PARAMS);
    requireCreditorPath(q, paFiscalCode);
    QuerySupport.checkAggregate(q);
    return service.stations(callerResolver.resolve(), paFiscalCode, q);
  }

  // --- helpers --------------------------------------------------------------

  private static Set<String> withExtra(String... extra) {
    Set<String> set = new HashSet<>(QuerySupport.FILTERS);
    set.addAll(Set.of(extra));
    return Set.copyOf(set);
  }

  private static MultivaluedMap<String, String> query(UriInfo uriInfo) {
    return uriInfo.getQueryParameters();
  }

  private static void requireNoQuery(UriInfo uriInfo) {
    if (!uriInfo.getQueryParameters().isEmpty()) {
      throw Errors.invalidRequest();
    }
  }

  private static void requireCreditorPath(Map<String, String> q, String paFiscalCode) {
    if (q.containsKey("paFiscalCode") && !paFiscalCode.equals(q.get("paFiscalCode"))) {
      throw Errors.invalidRequest();
    }
    q.put("paFiscalCode", paFiscalCode);
  }
}
