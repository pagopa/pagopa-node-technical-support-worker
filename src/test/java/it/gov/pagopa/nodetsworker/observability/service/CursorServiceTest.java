package it.gov.pagopa.nodetsworker.observability.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.gov.pagopa.nodetsworker.observability.dto.PageDto;
import it.gov.pagopa.nodetsworker.observability.exception.ObservabilityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CursorServiceTest {

  private static final String PATH = "/api/payments";
  private final AtomicLong now = new AtomicLong(1_000_000L);
  private final Caller caller = Caller.authenticated("user@example.org");
  private CursorService cursors;
  private ObservabilityConfig config;

  @BeforeEach
  void setUp() {
    cursors = new CursorService();
    cursors.clock = now::get;
    config = new ObservabilityConfig();
    config.pageDefaultSize = 20;
    config.pageMaxSize = 100;
    config.cursorTtlSeconds = 300;
  }

  @Test
  void nextPagesComeFromTheFirstPageSnapshotWhenDataChanges() {
    PageDto<String> first = cursors.paginate(List.of("a", "b", "c", "d", "e"), caller, PATH,
        query(null), "key", 1, config);
    assertEquals(List.of("a", "b"), first.getItems());
    assertEquals(1, first.getSnapshotRevision());

    // Live traffic between pages: a row is inserted before the cursor, another one disappears.
    List<String> changed = List.of("0", "a", "b", "d", "e", "f");
    PageDto<String> second = cursors.paginate(changed, caller, PATH, query(first.getNextCursor()),
        "key", 7, config);
    assertEquals(List.of("c", "d"), second.getItems());
    assertEquals(1, second.getSnapshotRevision());

    PageDto<String> third = cursors.paginate(changed, caller, PATH, query(second.getNextCursor()),
        "key", 9, config);
    assertEquals(List.of("e"), third.getItems());
    assertEquals(1, third.getSnapshotRevision());
    assertNull(third.getNextCursor());
  }

  @Test
  void snapshotIsNotAffectedByMutationsOfTheSourceList() {
    List<String> items = new ArrayList<>(List.of("a", "b", "c"));
    PageDto<String> first = cursors.paginate(items, caller, PATH, query(null), "key", 1, config);
    items.clear();
    PageDto<String> second = cursors.paginate(items, caller, PATH, query(first.getNextCursor()),
        "key", 2, config);
    assertEquals(List.of("c"), second.getItems());
  }

  @Test
  void cursorExpiresAfterTheTtl() {
    PageDto<String> first = cursors.paginate(List.of("a", "b", "c", "d", "e"), caller, PATH,
        query(null), "key", 1, config);

    now.addAndGet(299_999L);
    PageDto<String> second = cursors.paginate(List.of(), caller, PATH, query(first.getNextCursor()),
        "key", 1, config);
    assertNotNull(second.getNextCursor());

    // The TTL restarts from each issued cursor.
    now.addAndGet(300_000L);
    ObservabilityException expired = assertThrows(ObservabilityException.class,
        () -> cursors.paginate(List.of(), caller, PATH, query(second.getNextCursor()), "key", 1, config));
    assertEquals("CURSOR_EXPIRED", expired.toBody().getCode());
  }

  @Test
  void cursorRemainsBoundToTheSearch() {
    PageDto<String> first = cursors.paginate(List.of("a", "b", "c"), caller, PATH, query(null), "key",
        1, config);
    Map<String, String> otherSearch = new java.util.HashMap<>(query(first.getNextCursor()));
    otherSearch.put("psp", "OTHER");
    ObservabilityException misbound = assertThrows(ObservabilityException.class,
        () -> cursors.paginate(List.of(), caller, PATH, otherSearch, "key", 1, config));
    assertEquals("INVALID_REQUEST", misbound.toBody().getCode());
    ObservabilityException otherCaller = assertThrows(ObservabilityException.class,
        () -> cursors.paginate(List.of(), Caller.authenticated("other@example.org"), PATH,
            query(first.getNextCursor()), "key", 1, config));
    assertEquals("INVALID_REQUEST", otherCaller.toBody().getCode());
  }

  @Test
  void storeIsBoundedByEvictingTheOldestCursor() {
    cursors.maxEntries = 2;
    PageDto<String> oldest = cursors.paginate(List.of("a", "b", "c"), caller, PATH, query(null), "key",
        1, config);
    now.addAndGet(1);
    PageDto<String> middle = cursors.paginate(List.of("a", "b", "c"), caller, PATH, query(null), "key",
        1, config);
    now.addAndGet(1);
    cursors.paginate(List.of("a", "b", "c"), caller, PATH, query(null), "key", 1, config);

    ObservabilityException evicted = assertThrows(ObservabilityException.class,
        () -> cursors.paginate(List.of(), caller, PATH, query(oldest.getNextCursor()), "key", 1, config));
    assertEquals("CURSOR_EXPIRED", evicted.toBody().getCode());
    assertEquals(List.of("c"),
        cursors.paginate(List.of(), caller, PATH, query(middle.getNextCursor()), "key", 1, config).getItems());
  }

  private static Map<String, String> query(String cursor) {
    return cursor == null ? Map.of("pageSize", "2") : Map.of("pageSize", "2", "cursor", cursor);
  }
}
