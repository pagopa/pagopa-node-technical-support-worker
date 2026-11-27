package it.gov.pagopa.nodetsworker.observability.auth;

/**
 * Identity carried by a verified Entra ID token.
 *
 * @param oid   immutable object id of the user in the tenant
 * @param email {@code email}, falling back to {@code preferred_username}/{@code upn}
 * @param name  display name, falling back to the email
 */
public record EntraUser(String oid, String email, String name) {}
