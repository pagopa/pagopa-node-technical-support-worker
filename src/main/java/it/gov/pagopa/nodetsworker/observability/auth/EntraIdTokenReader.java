package it.gov.pagopa.nodetsworker.observability.auth;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

/**
 * Reads the identity carried by a Microsoft Entra ID token (the MSAL ID token of the frontend). The token
 * is <b>not</b> verified here: signature, issuer, audience and {@code exp}/{@code nbf} are validated by
 * Azure API Management before the request reaches the worker.
 */
@ApplicationScoped
public class EntraIdTokenReader {

  private static final Logger LOG = Logger.getLogger(EntraIdTokenReader.class);

  private final JwtConsumer consumer = new JwtConsumerBuilder()
      .setSkipAllValidators()
      .setDisableRequireSignature()
      .setSkipSignatureVerification()
      .build();

  /** @return the signed-in user, or {@code null} when the token cannot be parsed or has no object id. */
  public EntraUser read(String token) {
    if (token == null || token.isBlank()) {
      return null;
    }
    try {
      JwtClaims claims = consumer.processToClaims(token);
      String oid = claims.getStringClaimValue("oid");
      if (isBlank(oid)) {
        return null;
      }
      String email = firstNonBlank(claims.getStringClaimValue("email"),
          claims.getStringClaimValue("preferred_username"), claims.getStringClaimValue("upn"), oid);
      return new EntraUser(oid, email, firstNonBlank(claims.getStringClaimValue("name"), email));
    } catch (InvalidJwtException | MalformedClaimException e) {
      LOG.debugf("Entra ID token not readable: %s", e.getMessage());
      return null;
    }
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (!isBlank(value)) {
        return value;
      }
    }
    return null;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
