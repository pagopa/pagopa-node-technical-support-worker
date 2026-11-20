package it.gov.pagopa.nodetsworker.observability.apiconfig;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import it.gov.pagopa.nodetsworker.observability.dto.MaintenancesDto;
import it.gov.pagopa.nodetsworker.observability.util.IsoTime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

/**
 * In-memory cache of the scheduled station maintenances starting in [now, now + 7 days], read from
 * apiconfig at startup, lazily on first use if still empty, and every midnight. A failed refresh keeps
 * the previous data and flags it as stale.
 */
@ApplicationScoped
public class MaintenanceCache {
  private static final Logger LOG = Logger.getLogger(MaintenanceCache.class);

  @Inject @RestClient ApiConfigClient client;
  @Inject MaintenanceConfig config;

  private volatile MaintenancesDto snapshot;
  private volatile boolean stale;

  void onStart(@Observes StartupEvent event) {
    if (config.preloadOnStartup()) {
      refresh();
    }
  }

  @Scheduled(cron = "{observability.maintenance.cron:0 0 0 * * ?}",
      timeZone = "{observability.maintenance.zone:Europe/Rome}",
      concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
  void midnightRefresh() {
    refresh();
  }

  /** The cached snapshot, loading it first if no refresh has ever succeeded. Empty if upstream is down. */
  public Optional<MaintenancesDto> current() {
    if (snapshot == null) {
      refresh();
    }
    MaintenancesDto current = snapshot;
    if (current == null) {
      return Optional.empty();
    }
    current.setStale(stale);
    return Optional.of(current);
  }

  synchronized void refresh() {
    Instant now = Instant.now();
    Instant until = now.plus(Duration.ofDays(config.windowDays()));
    try {
      String from = IsoTime.format(now);
      String to = IsoTime.format(until);
      StationMaintenanceList response =
          client.aggregates(from, to, config.subscriptionKey().orElse(null));
      if (response.pageInfo() != null && response.pageInfo().totalPages() != null
          && response.pageInfo().totalPages() > 1) {
        LOG.warnf("Station maintenances span %d pages: only the first is cached", response.pageInfo().totalPages());
      }
      List<MaintenancesDto.Maintenance> items = (response.items() == null ? List.<StationMaintenanceList.Item>of()
          : response.items()).stream().map(MaintenanceCache::map)
          .sorted(Comparator.comparing(MaintenancesDto.Maintenance::getStartDateTime)
              .thenComparing(MaintenancesDto.Maintenance::getBrokerCode))
          .toList();
      snapshot = new MaintenancesDto(IsoTime.format(now), from, to, false, items);
      stale = false;
    } catch (RuntimeException failure) {
      // Only the failure type: messages may echo request headers, including the subscription key.
      String status = failure instanceof WebApplicationException web
          ? " (HTTP " + web.getResponse().getStatus() + ")" : "";
      LOG.warnf("Station maintenances refresh failed: %s%s", failure.getClass().getSimpleName(), status);
      stale = true;
    }
  }

  private static MaintenancesDto.Maintenance map(StationMaintenanceList.Item item) {
    List<MaintenancesDto.Creditor> creditors = item.creditorInstitutions() == null ? List.of()
        : item.creditorInstitutions().stream()
            .map(entity -> new MaintenancesDto.Creditor(entity.businessName(), entity.code())).toList();
    return new MaintenancesDto.Maintenance(item.brokerCode(), item.brokerName(), utc(item.startDateTime()),
        utc(item.endDateTime()), item.stations() == null ? List.of() : item.stations(), creditors);
  }

  private static String utc(String offsetDateTime) {
    return IsoTime.format(OffsetDateTime.parse(offsetDateTime).toInstant());
  }
}
