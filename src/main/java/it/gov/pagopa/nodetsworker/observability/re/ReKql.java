package it.gov.pagopa.nodetsworker.observability.re;

import java.util.Set;
import java.util.stream.Collectors;

/** Identifiers are server-configured and allowlisted; all values are SDK query parameters. */
final class ReKql {
  // Absent from the reviewed ADX schema; explicit nulls preserve the existing API contract.
  private static final Set<String> ABSENT_SOURCE_FIELDS = Set.of(
      "componente", "tipoVersamento", "fruitore", "erogatore", "parametriSpecificiInterfaccia",
      "esito", "info", "businessProcess", "fruitoreDescr", "erogatoreDescr", "standIn", "version");

  private ReKql() { }

  static String text(ReConfig config) {
    return text(config, false);
  }

  /**
   * {@code positionScope} drops the {@code paymentToken} key: every event of the PA + NAV pair, including
   * token-less calls such as verifyPaymentNotice, which belong to the position and not to an attempt.
   */
  static String text(ReConfig config, boolean positionScope) {
    config.requireReady();
    // fromLocal/toLocal/afterTime are Europe/Rome wall-clock values (see ReTime): insertedTimestamp is
    // local time. watermark is compared with ingestion_time(), which is a real UTC instant.
    return """
        declare query_parameters(pa:string,nav:string,""" + (positionScope ? "" : "token:string,") + """
        fromLocal:datetime,toLocal:datetime,
          watermark:datetime,hasAfter:bool,afterTime:datetime,afterId:string,takeRows:int);
        """
        + config.table.orElseThrow() + "\n"
        + "| where idDominio == pa and noticeNumber == nav"
        + (positionScope ? "" : " and paymentToken == token") + "\n"
        + "| where insertedTimestamp >= fromLocal and insertedTimestamp < toLocal\n"
        + "| where ingestion_time() <= watermark\n"
        + "| extend __reTime = insertedTimestamp, __reId = tostring("
        + config.rowIdColumn.orElseThrow() + ")\n"
        + "| where not(hasAfter) or __reTime > afterTime or (__reTime == afterTime and strcmp(__reId, afterId) > 0)\n"
        + "| order by __reTime asc, __reId asc\n"
        + "| take takeRows\n"
        + "| project " + ReEvent.FIELDS.stream()
            .map(field -> ABSENT_SOURCE_FIELDS.contains(field) ? field + " = dynamic(null)" : field)
            .collect(Collectors.joining(", ")) + ", __reTime, __reId";
  }
}
