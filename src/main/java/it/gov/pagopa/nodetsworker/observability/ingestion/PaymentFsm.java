package it.gov.pagopa.nodetsworker.observability.ingestion;

import it.gov.pagopa.nodetsworker.observability.ingestion.AdmissionFilter.Entity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The payment finite state machine (spec §2, §4.3 and {@code FSM stati di pagamento.md}). It encodes
 * the allowed transitions of the logical position and of each attempt, including the legacy
 * {@code *_NORPT} / {@code NOTICE_STORED} paths, and drives regression prevention and predecessor
 * inference:
 *
 * <ul>
 *   <li>only transitions allowed from the current state are applied; duplicates, no-ops and backward
 *       moves are ignored, and a terminal attempt is never reopened by a late event of the same
 *       token;</li>
 *   <li>when the current state reaches the event state through a <em>unique</em> FSM path, the
 *       intermediate states are reconstructed as {@code INFERRED}; when several paths are compatible
 *       the history is not invented — only the event state is appended and the timeline is marked
 *       incomplete.</li>
 * </ul>
 *
 * The FSM is value-based and side-effect free, so it is unit-testable in isolation.
 */
public final class PaymentFsm {

  private PaymentFsm() {
    throw new IllegalStateException("Utility class");
  }

  /** Initial state of an attempt when activation is the first observed event. */
  private static final String ATTEMPT_START = "PAYING";

  /** Canonical start of a receipt recipient's lifecycle (FSM della ricevuta per destinatario). */
  private static final String RECIPIENT_START = "NOTICE_GENERATED";

  private static final Map<String, Set<String>> LOGICAL = Map.of(
      "INSERTED", Set.of("PAYING"),
      "PAYING", Set.of("INSERTED", "PAID"),
      "PAID", Set.of("NOTIFIED", "NOTICE_STORED"),
      "NOTICE_STORED", Set.of("NOTIFIED"),
      "NOTIFIED", Set.of());

  private static final Map<String, Set<String>> ATTEMPT = Map.ofEntries(
      Map.entry("PAYING", Set.of("CANCELLED", "PAYMENT_RESERVED", "PAID", "PAID_NORPT", "FAILED", "FAILED_NORPT")),
      Map.entry("PAYING_RPT", Set.of("PAID", "PAID_NORPT", "FAILED", "FAILED_NORPT", "CANCELLED")),
      Map.entry("PAYMENT_RESERVED", Set.of("PAYMENT_SENT")),
      Map.entry("PAYMENT_SENT", Set.of("PAYMENT_ACCEPTED", "PAYMENT_REFUSED", "PAYMENT_UNKNOWN", "PAYMENT_SEND_ERROR")),
      Map.entry("PAYMENT_ACCEPTED", Set.of("PAID")),
      Map.entry("PAYMENT_REFUSED", Set.of("CANCELLED")),
      Map.entry("PAYMENT_SEND_ERROR", Set.of("CANCELLED")),
      Map.entry("PAYMENT_UNKNOWN", Set.of("PAID", "CANCELLED")),
      Map.entry("PAID_NORPT", Set.of("PAID", "NOTICE_GENERATED", "NOTICE_STORED")),
      Map.entry("CANCELLED_NORPT", Set.of("CANCELLED", "PAID", "PAID_NORPT", "FAILED", "FAILED_NORPT")),
      Map.entry("FAILED_NORPT", Set.of("FAILED")),
      Map.entry("PAID", Set.of("NOTICE_GENERATED", "NOTICE_STORED")),
      Map.entry("NOTICE_GENERATED", Set.of("NOTICE_SENT", "NOTICE_STORED")),
      Map.entry("NOTICE_SENT", Set.of("NOTIFIED")),
      Map.entry("NOTICE_STORED", Set.of("NOTIFIED")),
      // Terminal attempt states.
      Map.entry("CANCELLED", Set.of()),
      Map.entry("FAILED", Set.of()),
      Map.entry("NOTIFIED", Set.of()));

  /**
   * Receipt-recipient FSM (spec {@code FSM stati di pagamento.md} §3): generation, send, the
   * terminal {@code NOTIFIED}, and the {@code NOTICE_PENDING} retry loop back to {@code NOTICE_SENT}.
   */
  private static final Map<String, Set<String>> RECIPIENT = Map.of(
      "NOTICE_GENERATED", Set.of("NOTICE_SENT"),
      "NOTICE_SENT", Set.of("NOTIFIED", "NOTICE_PENDING"),
      "NOTICE_PENDING", Set.of("NOTICE_SENT"),
      "NOTIFIED", Set.of());

  /**
   * Outcome of resolving an event state against the current state.
   *
   * @param accepted whether a transition is applied at all (false = duplicate / regression / unknown)
   * @param path     ordered states to append (empty when not accepted); the last is the event state
   *                 reached as {@code SOURCE_EVENT}, the preceding ones are reconstructed as
   *                 {@code INFERRED}
   * @param inferredCount how many leading entries of {@code path} are inferred predecessors
   * @param ambiguous whether the event state was reached but predecessors could not be rebuilt
   *                  unambiguously (timeline is incomplete, no predecessors invented)
   */
  public record Resolution(boolean accepted, List<String> path, int inferredCount, boolean ambiguous) {

    static Resolution rejected() {
      return new Resolution(false, List.of(), 0, false);
    }
  }

  private static Map<String, Set<String>> graph(Entity entity) {
    return switch (entity) {
      case POSITION -> LOGICAL;
      case PAYMENT -> ATTEMPT;
      case RECIPIENT -> RECIPIENT;
    };
  }

  /** Whether {@code state} is a known state of the entity's FSM. */
  public static boolean isKnownState(Entity entity, String state) {
    return graph(entity).containsKey(state);
  }

  /** A direct, FSM-allowed transition {@code from -> to}. */
  public static boolean isAllowed(Entity entity, String from, String to) {
    Set<String> next = graph(entity).get(from);
    return next != null && next.contains(to);
  }

  /**
   * Resolve the event state against the current state of the entity.
   *
   * @param currentState the entity's current state, or {@code null} for a brand-new entity/attempt
   * @param eventState   the bare state carried by the admitted event
   */
  public static Resolution resolve(Entity entity, String currentState, String eventState) {
    Map<String, Set<String>> graph = graph(entity);
    if (eventState == null || !graph.containsKey(eventState)) {
      // Unknown state label: not a transition we can place on this entity's FSM.
      return Resolution.rejected();
    }

    if (currentState == null) {
      if (entity == Entity.PAYMENT) {
        if (eventState.equals(ATTEMPT_START)) {
          return new Resolution(true, List.of(eventState), 0, false);
        }
        if (eventState.equals("PAYMENT_RESERVED")) {
          // V1 PA OLD can create a reserved attempt without an observed PAYING transition.
          return new Resolution(true, List.of(eventState), 0, true);
        }
        if (Set.of("PAYMENT_SENT", "PAYMENT_ACCEPTED", "PAYMENT_REFUSED",
            "PAYMENT_UNKNOWN", "PAYMENT_SEND_ERROR").contains(eventState)) {
          // PA OLD may start at PAYMENT_RESERVED; never invent PAYING on the first observation.
          List<String> path = new ArrayList<>();
          path.add("PAYMENT_RESERVED");
          if (!eventState.equals("PAYMENT_SENT")) {
            path.add("PAYMENT_SENT");
          }
          path.add(eventState);
          return new Resolution(true, List.copyOf(path), path.size() - 1, true);
        }
        // No documented unique entry path is known for the remaining legacy/outcome states.
        return new Resolution(true, List.of(eventState), 0, true);
      }
      if (entity == Entity.RECIPIENT) {
        if (RECIPIENT_START.equals(eventState)) {
          return new Resolution(true, List.of(eventState), 0, false);
        }
        // Reconstruct predecessors when the canonical start reaches the event state uniquely.
        List<List<String>> paths = new ArrayList<>();
        findPaths(RECIPIENT, RECIPIENT_START, eventState, new LinkedHashSet<>(), paths);
        if (paths.size() == 1) {
          List<String> path = paths.get(0);
          return new Resolution(true, List.copyOf(path), path.size() - 1, false);
        }
        return new Resolution(true, List.of(eventState), 0, true);
      }
      if ("INSERTED".equals(eventState) || "PAYING".equals(eventState)) {
        return new Resolution(true, List.of(eventState), 0, false);
      }
      if ("PAID".equals(eventState)) {
        return new Resolution(true, List.of("PAYING", "PAID"), 1, false);
      }
      // Legacy receipt states have no verified entry path from the canonical start.
      return new Resolution(true, List.of(eventState), 0, true);
    }

    if (eventState.equals(currentState)) {
      // Duplicate / no-op.
      return Resolution.rejected();
    }
    if (isAllowed(entity, currentState, eventState)) {
      return new Resolution(true, List.of(eventState), 0, false);
    }
    if (entity == Entity.PAYMENT && "NOTICE_STORED".equals(currentState)
        && "NOTICE_SENT".equals(eventState)) {
      // Nodo's resend gate is unresolved: retain observed evidence without declaring a generic edge
      // or inferring the missing prerequisite (for example, a late RPT).
      return new Resolution(true, List.of(eventState), 0, true);
    }
    return fromReachable(graph, currentState, eventState);
  }

  /**
   * The event state is not a direct successor: try to reach it from {@code origin}. A single simple
   * path reconstructs the predecessors as inferred; several paths are ambiguous (append the event
   * state only, mark incomplete); none means a regression/unknown move (rejected).
   *
   */
  private static Resolution fromReachable(Map<String, Set<String>> graph, String origin, String target) {
    List<List<String>> paths = new ArrayList<>();
    findPaths(graph, origin, target, new LinkedHashSet<>(), paths);
    if (paths.isEmpty()) {
      return Resolution.rejected();
    }
    if (paths.size() > 1) {
      // Several compatible paths: do not invent history, append only the event state.
      return new Resolution(true, List.of(target), 0, true);
    }
    List<String> path = paths.get(0); // origin ... target
    List<String> toAppend = path.subList(1, path.size());
    return new Resolution(true, List.copyOf(toAppend), toAppend.size() - 1, false);
  }

  private static void findPaths(Map<String, Set<String>> graph, String from, String target,
      LinkedHashSet<String> visited, List<List<String>> out) {
    if (!visited.add(from)) {
      return;
    }
    if (from.equals(target)) {
      out.add(new ArrayList<>(visited));
    } else {
      for (String next : graph.getOrDefault(from, Set.of())) {
        findPaths(graph, next, target, visited, out);
        if (out.size() > 1) {
          break; // ambiguity already established; stop early
        }
      }
    }
    visited.remove(from);
  }
}
