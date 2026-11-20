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
import java.util.regex.Pattern;

/**
 * Opaque, server-stored pagination cursors with a 5-minute TTL, bound to the caller, the normalized
 * query, the page size, the sort and the aggregate revision. Mirrors {@code page()} in the mock:
 * a stale cursor or a revision change yields {@code 410 CURSOR_EXPIRED}.
 */
@ApplicationScoped
public class CursorService {

  private static final Pattern CURSOR = Pattern.compile("^[\\w-]{32}$");
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Entry> cursors = new ConcurrentHashMap<>();

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

    long nowMs = System.currentTimeMillis();
    int index = 0;
    String cursor = query.get("cursor");
    if (cursor != null) {
      if (!CURSOR.matcher(cursor).matches()) {
        throw Errors.invalidRequest("Cursore non valido.");
      }
      Entry entry = cursors.get(cursor);
      if (entry == null || entry.expires <= nowMs || entry.revision != revision) {
        throw Errors.cursorExpired();
      }
      if (!entry.signature.equals(signature)) {
        throw Errors.invalidRequest("Il cursore non appartiene alla ricerca.");
      }
      index = entry.index;
    }

    String nextCursor = null;
    if (items.size() > index + size) {
      nextCursor = opaque();
      cursors.put(nextCursor, new Entry(signature, index + size,
          nowMs + config.cursorTtlSeconds() * 1000L, revision));
    }
    cursors.entrySet().removeIf(e -> e.getValue().expires <= nowMs);

    int toIndex = Math.min(items.size(), index + size);
    List<T> slice = items.subList(Math.min(index, items.size()), toIndex);
    return new PageDto<>(List.copyOf(slice), nextCursor, revision);
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

    Entry(String signature, int index, long expires, long revision) {
      this.signature = signature;
      this.index = index;
      this.expires = expires;
      this.revision = revision;
    }
  }
}
