package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.dto.CacheVersionsDto;
import it.gov.pagopa.nodetsworker.observability.monitoring.CacheVersion;
import it.gov.pagopa.nodetsworker.observability.monitoring.MonitoringConfig;
import it.gov.pagopa.nodetsworker.observability.monitoring.NodoMonitoringClient;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/** Queries every monitoring target in parallel and aggregates the outcomes, one entry per node. */
@ApplicationScoped
public class NodoMonitoringService {
  private static final Logger LOG = Logger.getLogger(NodoMonitoringService.class);
  @Inject @RestClient NodoMonitoringClient client;
  @Inject MonitoringConfig config;

  public CacheVersionsDto versions() {
    List<MonitoringConfig.Node> nodes = config.nodes();
    List<CompletableFuture<CacheVersionsDto.Node>> calls = nodes.stream().map(this::call).toList();
    return new CacheVersionsDto(IsoTime.format(Instant.now()),
        calls.stream().map(CompletableFuture::join).toList());
  }

  private CompletableFuture<CacheVersionsDto.Node> call(MonitoringConfig.Node node) {
    if (node.subscriptionKey().isEmpty()) {
      return CompletableFuture.completedFuture(empty(node, "UNCONFIGURED"));
    }
    CompletableFuture<CacheVersion> response;
    try {
      response = client.cacheVersion(node.target(), node.subscriptionKey().get()).toCompletableFuture();
    } catch (RuntimeException failure) {
      response = CompletableFuture.failedFuture(failure);
    }
    return response.orTimeout(config.timeoutSeconds(), TimeUnit.SECONDS)
        .thenApply(version -> new CacheVersionsDto.Node(node.name(), node.target(), "OK",
            version.dbStatus(), version.cacheId(), version.version(), version.environment(),
            version.serviceIdentifier()))
        .exceptionally(failure -> {
          // Only the failure type: messages may echo request headers, including the subscription key.
          LOG.warnf("Nodo monitoring cache version unavailable for %s: %s", node.name(),
              failure.getClass().getSimpleName());
          return empty(node, "UNAVAILABLE");
        });
  }

  private static CacheVersionsDto.Node empty(MonitoringConfig.Node node, String outcome) {
    return new CacheVersionsDto.Node(node.name(), node.target(), outcome, null, null, null, null, null);
  }
}
