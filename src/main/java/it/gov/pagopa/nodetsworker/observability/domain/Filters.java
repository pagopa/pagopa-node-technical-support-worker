package it.gov.pagopa.nodetsworker.observability.domain;

import lombok.Builder;
import lombok.Data;

/**
 * Functional filters shared by list and aggregate endpoints. A {@code null} field means "no filter";
 * the sentinel {@link #UNKNOWN} selects attempts whose dimension is unavailable (null).
 */
@Data
@Builder
public class Filters {
  public static final String UNKNOWN = "~unknown";

  private String paFiscalCode;
  private String noticeNumber;
  private String logicalState;
  private String attemptState;
  private String serviceIdentifier;
  private String psp;
  private String canale;
  private String stazione;
  private String anomaliesOnly;
  private String unresolvedOnly;

  public boolean wantsAnomaliesOnly() {
    return "true".equals(anomaliesOnly);
  }

  public boolean wantsUnresolvedOnly() {
    return "true".equals(unresolvedOnly);
  }
}
