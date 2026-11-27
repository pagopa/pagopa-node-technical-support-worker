package it.gov.pagopa.nodetsworker.observability.re;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-process fail-fast admission; no payment identifiers are retained. */
@ApplicationScoped
public class ReLimiter {
  private int active;
  private long minute = Long.MIN_VALUE;
  private int requests;

  synchronized Lease acquire(int concurrency, int perMinute, Instant now) {
    long currentMinute = now.getEpochSecond() / 60;
    if (currentMinute != minute) {
      minute = currentMinute;
      requests = 0;
    }
    if (active >= concurrency || requests >= perMinute) {
      throw ReErrors.error(429, "RE_THROTTLED", "Limite richieste RE raggiunto. Riprovare più tardi.");
    }
    active++;
    requests++;
    return new Lease();
  }

  final class Lease implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        synchronized (ReLimiter.this) {
          active--;
        }
      }
    }
  }
}
