package it.gov.pagopa.nodetsworker.observability.service;

import it.gov.pagopa.nodetsworker.observability.dto.PageDto;
import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import jakarta.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Opaque, server-stored pagination cursors with a 5-minute TTL (renewed for each issued cursor),
 * bound to the caller, the normalized query, the page size and the sort. The first page freezes the
 * sorted result list and its aggregate revision: every following page is served from that snapshot,
 * so live updates between pages cause neither {@code 410} nor duplicated/skipped rows, and
 * {@code snapshotRevision} stays the first page's revision (newer SSE revisions mean "results
 * changed"). An unknown, stale or evicted cursor yields {@code 410 CURSOR_EXPIRED}.
 */
@ApplicationScoped
public class CursorService {

  private static final Pattern CURSOR = Pattern.compile("^[\\w-]{32}$");
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Entry> cursors = new ConcurrentHashMap<>();
  LongSupplier clock = System::currentTimeMillis;
  /** Bounds the snapshots kept in memory; the cursor closest to expiry is evicted first. */
  int maxEntries = 2048;

  public <T> PageDto<T> paginate(List<T> items, Caller caller, String path, Map<String, String> query,
      String effectiveSort, long revision, ObservabilityConfig config) {
    String pageSizeRaw = query.getOrDefault("pageSize", String.valueOf(config.pageDefaultSize()));
    if (!pageSizeRaw.matches("\\d+")) {
      throw Errors.invalidRequest();
    }
    int size;
    try {
      size = Integer.parseInt(pageSizeRaw);
    } catch (NumberFormatException e) {
      throw Errors.invalidRequest();
    }
    if (size < 1 || size > config.pageMaxSize()) {
      throw Errors.invalidRequest();
    }

    TreeMap<String, String> normalized = new TreeMap<>(query);
    normalized.remove("cursor");
    String signature = caller.id() + "|" + path + "|" + normalized + "|" + size + "|" + effectiveSort;

    long nowMs = clock.getAsLong();
    int index = 0;
    List<?> snapshot = null;
    long snapshotRevision = revision;
    String cursor = query.get("cursor");
    if (cursor != null) {
      if (!CURSOR.matcher(cursor).matches()) {
        throw Errors.invalidRequest("Cursore non valido.");
      }
      Entry entry = cursors.get(cursor);
      if (entry == null || entry.expires <= nowMs) {
        throw Errors.cursorExpired();
      }
      if (!entry.signature.equals(signature)) {
        throw Errors.invalidRequest("Il cursore non appartiene alla ricerca.");
      }
      index = entry.index;
      snapshot = entry.snapshot;
      snapshotRevision = entry.revision;
    }

    @SuppressWarnings("unchecked")
    List<T> rows = snapshot != null ? (List<T>) snapshot : List.copyOf(items);

    String nextCursor = null;
    if (rows.size() > index + size) {
      nextCursor = opaque();
      cursors.put(nextCursor, new Entry(signature, index + size,
          nowMs + config.cursorTtlSeconds() * 1000L, snapshotRevision, rows));
    }
    cursors.entrySet().removeIf(e -> e.getValue().expires <= nowMs);
    while (cursors.size() > maxEntries) {
      cursors.entrySet().stream()
          .min((a, b) -> Long.compare(a.getValue().expires, b.getValue().expires))
          .ifPresent(oldest -> cursors.remove(oldest.getKey()));
    }

    int toIndex = Math.min(rows.size(), index + size);
    List<T> slice = rows.subList(Math.min(index, rows.size()), toIndex);
    return new PageDto<>(List.copyOf(slice), nextCursor, snapshotRevision);
  }

  private String opaque() {
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static final class Entry {
    private final String signature;
    private final int index;
    private final long expires;
    private final long revision;
    private final List<?> snapshot;

    Entry(String signature, int index, long expires, long revision, List<?> snapshot) {
      this.signature = signature;
      this.index = index;
      this.expires = expires;
      this.revision = revision;
      this.snapshot = snapshot;
    }
  }
}
