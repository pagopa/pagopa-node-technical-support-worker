package it.gov.pagopa.nodetsworker.observability.apiconfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import it.gov.pagopa.nodetsworker.observability.dto.MaintenancesDto;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import jakarta.ws.rs.ProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MaintenanceCacheTest {
  private ApiConfigClient client;
  private MaintenanceCache cache;

  @BeforeEach
  void setUp() {
    client = mock(ApiConfigClient.class);
    MaintenanceConfig config = new MaintenanceConfig();
    config.windowDays = 7;
    cache = new MaintenanceCache();
    cache.client = client;
    cache.config = config;
  }

  private static StationMaintenanceList payload() {
    return new StationMaintenanceList(List.of(
        new StationMaintenanceList.Item("B2", "Two", "2026-11-06T10:00:00.000+01:00", "2026-11-06T12:00:00.000+01:00",
            List.of("B2_01"), List.of()),
        new StationMaintenanceList.Item("B1", "One", "2026-11-05T10:00:00.000+01:00", "2026-11-05T11:00:00.000+01:00",
            List.of("B1_01", "B1_02"), List.of(new StationMaintenanceList.Creditor("Ente", "00000000001")))),
        new StationMaintenanceList.PageInfo(1));
  }

  @Test
  void loadsOnceForTheNextSevenDaysAndServesFromCache() {
    when(client.aggregates(anyString(), anyString(), any())).thenReturn(payload());

    MaintenancesDto first = cache.current().orElseThrow();
    MaintenancesDto second = cache.current().orElseThrow();

    ArgumentCaptor<String> from = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
    verify(client, times(1)).aggregates(from.capture(), to.capture(), isNull());
    assertEquals(Duration.ofDays(7), Duration.between(Instant.parse(from.getValue()), Instant.parse(to.getValue())));
    assertSame(first.getItems(), second.getItems());
    assertFalse(first.isStale());
    assertEquals(List.of("B1", "B2"), first.getItems().stream().map(MaintenancesDto.Maintenance::getBrokerCode).toList());
    assertEquals("2026-11-05T09:00:00.000Z", first.getItems().get(0).getStartDateTime());
    assertEquals("00000000001", first.getItems().get(0).getCreditorInstitutions().get(0).getCode());
  }

  @Test
  void keepsPreviousDataAsStaleWhenARefreshFails() {
    when(client.aggregates(anyString(), anyString(), any())).thenReturn(payload());
    cache.refresh();
    when(client.aggregates(anyString(), anyString(), any())).thenThrow(new ProcessingException("down"));

    cache.refresh();

    MaintenancesDto current = cache.current().orElseThrow();
    assertTrue(current.isStale());
    assertEquals(2, current.getItems().size());
  }

  @Test
  void isEmptyWhenUpstreamNeverAnswered() {
    when(client.aggregates(anyString(), anyString(), any())).thenThrow(new ProcessingException("down"));

    assertTrue(cache.current().isEmpty());
  }
}
