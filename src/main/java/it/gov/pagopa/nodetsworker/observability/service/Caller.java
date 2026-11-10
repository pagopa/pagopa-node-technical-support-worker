package it.gov.pagopa.nodetsworker.observability.service;

/**
 * The authenticated caller. Authentication is deferred (the worker sits behind APIM), so a single
 * implicit identity is used for now; the fields are the seam where real roles will plug in later.
 *
 * @param id            stable actor id (used for audit and cursor binding)
 * @param role          functional role label
 * @param allowedPa     the only visible creditor, or {@code null} for unrestricted visibility
 * @param canReadTokens whether token lookup/reveal is permitted
 * @param canMock       whether mock-only controls are permitted (never in production)
 */
public record Caller(String id, String role, String allowedPa, boolean canReadTokens, boolean canMock) {

  /**
   * The implicit identity used while authentication is deferred: full read access, token capable.
   * Values stay within the functional contract enums ({@code Me.id}/{@code Me.role}).
   */
  public static Caller implicit() {
    return new Caller("demo-operator", "operator", null, true, false);
  }

  public boolean allows(String paFiscalCode) {
    return allowedPa == null || allowedPa.equals(paFiscalCode);
  }
}
