package it.gov.pagopa.nodetsworker.observability.re;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Conservative routing-equivalent privacy check; never retains or logs a target/query. */
public final class ReDiagnosticPaths {
  private ReDiagnosticPaths() { }

  public static boolean suppressRawTarget(String target) {
    return suppressPath(target == null ? null : withoutQueryOrFragment(target), true);
  }

  public static boolean suppressDecodedPath(String path) {
    return suppressPath(path, false);
  }

  private static boolean suppressPath(String path, boolean rawTarget) {
    if (path == null) {
      return false;
    }
    for (int pass = 0; pass < 8; pass++) {
      // Decoded delimiters may belong to a path parameter, not the actual query/fragment.
      if (diagnosticPath(path) || (rawTarget && diagnosticPath(withoutQueryOrFragment(path)))) {
        return true;
      }
      if (!path.contains("%")) {
        return false;
      }
      try {
        // URLDecoder otherwise treats literal '+' as a query-form space, not a path character.
        String decoded = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
        if (decoded.equals(path)) {
          return false;
        }
        path = decoded;
      } catch (IllegalArgumentException malformed) {
        return true;
      }
    }
    // Excessively nested encodings are ambiguous: drop telemetry rather than risk recording secrets.
    return true;
  }

  private static boolean diagnosticPath(String path) {
    // RESTEasy removes per-segment matrix parameters before selecting a resource.
    String routed = path.replaceAll(";[^/]*", "").replaceAll("/+$", "");
    return routed.contains("/attempts/") && routed.endsWith("/re-events");
  }

  private static String withoutQueryOrFragment(String target) {
    int query = target.indexOf('?');
    int fragment = target.indexOf('#');
    int end = query < 0 ? target.length() : query;
    return target.substring(0, fragment < 0 ? end : Math.min(end, fragment));
  }
}
