package it.gov.pagopa.nodetsworker.observability.domain;

import it.gov.pagopa.nodetsworker.observability.model.TimestampBasis;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Synthetic demo projection, a faithful port of {@code fixtures(now)} in {@code mocks/model.mjs}.
 * Used only to seed a local/demo projection; never represents production data.
 */
public final class Fixtures {

  private static final long DAY = 86_400_000L;

  private Fixtures() {
    throw new IllegalStateException("Utility class");
  }

  private record Spec(List<String> logical, List<List<String>> attempts, double age, boolean estimated, boolean missing) {
  }

  public static List<DomainPayment> build(Instant now) {
    long nowMs = now.toEpochMilli();
    List<Spec> data = List.of(
        new Spec(List.of("PAYING"),
            List.of(List.of("PAYING", "PAYMENT_RESERVED", "PAYMENT_SENT", "PAYMENT_UNKNOWN")), 1, false, false),
        new Spec(List.of("PAYING", "INSERTED", "PAYING", "PAID"),
            List.of(List.of("PAYING", "CANCELLED"),
                List.of("PAYING", "PAYMENT_RESERVED", "PAYMENT_SENT", "PAYMENT_ACCEPTED", "PAID")), 3, false, false),
        new Spec(List.of("PAYING", "INSERTED"),
            List.of(List.of("PAYING", "CANCELLED")), 59.9, false, false),
        new Spec(List.of("PAYING"),
            List.of(List.of("PAYING", "PAYMENT_RESERVED", "PAYMENT_SENT", "PAYMENT_SEND_ERROR")), 0.2, false, false),
        new Spec(List.of("PAYING", "PAID", "NOTICE_STORED"),
            List.of(List.of("PAYING_RPT", "PAID_NORPT", "PAID", "NOTICE_STORED")), 10, false, false),
        new Spec(List.of("PAYING", "PAID", "NOTIFIED"),
            List.of(List.of("PAYING", "PAID", "NOTICE_GENERATED", "NOTICE_SENT", "NOTIFIED")), 20, false, false),
        new Spec(List.of("PAYING"),
            List.of(List.of("PAYING", "PAYMENT_RESERVED", "PAYMENT_SENT")), 2, true, false),
        new Spec(List.of("PAYING"),
            List.of(List.of("PAYING")), 1, false, true));

    List<DomainPayment> payments = new ArrayList<>();
    for (int index = 0; index < data.size(); index++) {
      Spec item = data.get(index);
      long started = nowMs - (long) (item.age() * DAY);
      String paFiscalCode = index < 4 ? "00000000001" : "00000000002";
      String noticeNumber = "30000000000000000" + (index + 1);
      boolean incomplete = item.estimated() || item.missing();
      List<String> firstAttempt = item.attempts().get(0);

      List<TimelineItem> logical = new ArrayList<>();
      for (int i = 0; i < item.logical().size(); i++) {
        String state = item.logical().get(i);
        long at;
        if (index == 1) {
          at = new long[] {started, started + 60_000, started + DAY, started + DAY + 240_000}[i];
        } else if ("PAID".equals(state)) {
          at = started + (long) firstAttempt.indexOf("PAID") * 60_000;
        } else if ("NOTICE_STORED".equals(state) || "NOTIFIED".equals(state)) {
          at = started + (long) (firstAttempt.size() - 1) * 60_000;
        } else {
          at = started + (long) i * 60_000;
        }
        TimestampBasis basis = item.missing() ? TimestampBasis.ANOMALY
            : item.estimated() && i == 0 ? TimestampBasis.INFERRED : TimestampBasis.SOURCE_EVENT;
        logical.add(transition(state, at, basis));
      }

      List<DomainAttempt> attempts = new ArrayList<>();
      for (int a = 0; a < item.attempts().size(); a++) {
        List<String> states = item.attempts().get(a);
        long first = started + (long) a * DAY;
        List<TimelineItem> timeline = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
          long offset = (long) (item.estimated() ? Math.max(0, i - 1) : i) * 60_000;
          TimestampBasis basis = item.missing() ? TimestampBasis.ANOMALY
              : item.estimated() && i == 0 ? TimestampBasis.INFERRED : TimestampBasis.SOURCE_EVENT;
          timeline.add(transition(states.get(i), first + offset, basis));
        }
        attempts.add(DomainAttempt.builder()
            .attemptId(String.format("att_%03d_%d", index + 1, a + 1))
            .paymentToken("SYNTHETIC-TOKEN-" + (index + 1) + "-" + (a + 1))
            .serviceIdentifier(index % 2 == 1 ? "SYNTHETIC_SERVICE_B" : "SYNTHETIC_SERVICE_A")
            .psp(item.missing() ? null : index % 2 == 1 ? "ABI00002" : "ABI00001")
            .stazione(item.missing() ? null : paFiscalCode + (index % 2 == 1 ? "_02" : "_01"))
            .canale(item.missing() ? null : index % 2 == 1 ? "SYNTHETIC_CHANNEL_B" : "SYNTHETIC_CHANNEL_A")
            .attemptStartedAt(item.missing() ? null : Instant.ofEpochMilli(first))
            .startTimestampBasis(item.missing() ? TimestampBasis.ANOMALY
                : item.estimated() ? TimestampBasis.INFERRED : TimestampBasis.SOURCE_EVENT)
            .firstSeenAt(Instant.ofEpochMilli(first))
            .expiresAt(Instant.ofEpochMilli(first + 60 * DAY))
            .timelineIncomplete(incomplete)
            .timeline(timeline)
            .build());
      }

      payments.add(DomainPayment.builder()
          .paFiscalCode(paFiscalCode)
          .noticeNumber(noticeNumber)
          .projectionRevision(1)
          .timelineIncomplete(incomplete)
          .timeline(logical)
          .attempts(attempts)
          .build());
    }
    return payments;
  }

  private static TimelineItem transition(String state, long atMs, TimestampBasis basis) {
    Instant at = basis == TimestampBasis.ANOMALY ? null : Instant.ofEpochMilli(atMs);
    return new TimelineItem(state, at, basis);
  }
}
