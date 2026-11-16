package it.gov.pagopa.nodetsworker.observability.service;

/**
 * The authenticated caller. Users signed in with Microsoft Entra ID are operators with full visibility
 * (no role or group restrictions yet); the fields are the seam where real roles will plug in later.
 *
 * @param id            stable actor id (used for audit and cursor binding)
 * @param role          functional role label
 * @param allowedPa     the only visible creditor, or {@code null} for unrestricted visibility
 * @param canReadTokens whether token lookup/reveal is permitted
 * @param canMock       whether mock-only controls are permitted (never in production)
 */
public record Caller(String id, String role, String allowedPa, boolean canReadTokens, boolean canMock) {

  /**
   * The implicit identity used when authentication is disabled: full read access, token capable.
   * Values stay within the functional contract enums ({@code Me.id}/{@code Me.role}).
   */
  public static Caller implicit() {
    return new Caller("demo-operator", "operator", null, true, false);
  }

  /** A signed-in Entra ID user, identified by email: full read access, token capable. */
  public static Caller authenticated(String email) {
    return new Caller(email, "operator", null, true, false);
  }

  public boolean isImplicit() {
    return equals(implicit());
  }

  public boolean allows(String paFiscalCode) {
    return allowedPa == null || allowedPa.equals(paFiscalCode);
  }
}
