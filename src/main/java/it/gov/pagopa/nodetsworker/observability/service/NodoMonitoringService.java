package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.apiconfig.cache.ApiConfigCacheClient;
import it.gov.pagopa.nodetsworker.observability.dto.CacheVersionsDto;
import it.gov.pagopa.nodetsworker.observability.dto.CacheVersionsDto.CacheVersionOutcome;
import it.gov.pagopa.nodetsworker.observability.monitoring.CacheVersion;
import it.gov.pagopa.nodetsworker.observability.monitoring.MonitoringConfig;
import it.gov.pagopa.nodetsworker.observability.monitoring.NodoMonitoringClient;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/** Queries every monitoring target in parallel and aggregates the outcomes, one entry per node. */
@ApplicationScoped
public class NodoMonitoringService {
  private static final Logger LOG = Logger.getLogger(NodoMonitoringService.class);
  private static final String CACHE_SERVICE_IDENTIFIER = "CACHE";
  @Inject @RestClient NodoMonitoringClient client;
  @Inject @RestClient
  ApiConfigCacheClient apiConfigCacheClient;
  @Inject MonitoringConfig config;

  @ConfigProperty(name = "apiconfig-cache.subscription-key")
  Optional<String> apiConfigCacheSubscriptionKey = Optional.empty();

  @ConfigProperty(name = "app.environment")
  String environment;

  public CacheVersionsDto versions() {
    List<MonitoringConfig.Node> nodes = config.nodes();
    List<CompletableFuture<CacheVersionsDto.Node>> calls = nodes.stream().map(this::call).toList();

    // Runs while the node calls are in flight.
    CacheVersionsDto.Cache cache = apiConfigCache();

    return new CacheVersionsDto(IsoTime.format(Instant.now()),
        calls.stream().map(CompletableFuture::join).toList(), cache);
  }

  private CacheVersionsDto.Cache apiConfigCache() {
      if (apiConfigCacheSubscriptionKey.filter(key -> !key.isBlank()).isEmpty()) {
      return emptyCache(CacheVersionOutcome.UNCONFIGURED);
    }
    try (Response response = apiConfigCacheClient.cacheVersion()) {
      if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
        LOG.warnf("apiconfig-cache cache version unavailable: HTTP %d", response.getStatus());
        return emptyCache(CacheVersionOutcome.UNAVAILABLE);
      }
      return new CacheVersionsDto.Cache(CacheVersionOutcome.OK,
          response.getHeaderString(ApiConfigCacheClient.HEADER_X_CACHE_ID),
          response.getHeaderString(ApiConfigCacheClient.HEADER_X_CACHE_VERSION),
          response.getHeaderString(ApiConfigCacheClient.HEADER_X_CACHE_TIMESTAMP),
          environment, CACHE_SERVICE_IDENTIFIER);
    } catch (RuntimeException failure) {
      // Covers WebApplicationException (error status) and ProcessingException (I/O, timeout).
      LOG.warnf("apiconfig-cache cache version unavailable: %s", failure.getClass().getSimpleName());
      return emptyCache(CacheVersionOutcome.UNAVAILABLE);
    }
  }

  private CacheVersionsDto.Cache emptyCache(CacheVersionOutcome outcome) {
    return new CacheVersionsDto.Cache(outcome, null, null, null, environment, CACHE_SERVICE_IDENTIFIER);
  }

  private CompletableFuture<CacheVersionsDto.Node> call(MonitoringConfig.Node node) {
    if (node.subscriptionKey().isEmpty()) {
      return CompletableFuture.completedFuture(empty(node, CacheVersionOutcome.UNCONFIGURED));
    }
    CompletableFuture<CacheVersion> response;
    try {
      response = client.cacheVersion(node.target(), node.subscriptionKey().get()).toCompletableFuture();
    } catch (RuntimeException failure) {
      response = CompletableFuture.failedFuture(failure);
    }
    return response.orTimeout(config.timeoutSeconds(), TimeUnit.SECONDS)
        .thenApply(version -> new CacheVersionsDto.Node(node.name(), node.target(), CacheVersionOutcome.OK,
            version.dbStatus(), version.cacheId(), version.version(), version.environment(),
            version.serviceIdentifier()))
        .exceptionally(failure -> {
          // Only the failure type: messages may echo request headers, including the subscription key.
          LOG.warnf("Nodo monitoring cache version unavailable for %s: %s", node.name(),
              failure.getClass().getSimpleName());
          return empty(node, CacheVersionOutcome.UNAVAILABLE);
        });
  }

  private static CacheVersionsDto.Node empty(MonitoringConfig.Node node, CacheVersionOutcome outcome) {
    return new CacheVersionsDto.Node(node.name(), node.target(), outcome, null, null, null, null, null);
  }
}
