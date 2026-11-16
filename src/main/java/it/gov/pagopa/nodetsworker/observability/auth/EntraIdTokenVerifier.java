package it.gov.pagopa.nodetsworker.observability.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;

/**
 * Verifies a Microsoft Entra ID token (the MSAL ID token of the frontend): RS256 signature against the
 * tenant JWKS (cached and refreshed by jose4j on unknown key ids), v2.0 tenant issuer, audience equal to
 * the app client id, {@code exp}/{@code nbf}, and an object id.
 */
@ApplicationScoped
public class EntraIdTokenVerifier {

  private static final Logger LOG = Logger.getLogger(EntraIdTokenVerifier.class);
  private static final int CLOCK_SKEW_SECONDS = 60;

  @Inject
  AuthConfig config;

  private JwtConsumer consumer;

  public EntraIdTokenVerifier() {
  }

  EntraIdTokenVerifier(String issuer, String clientId, VerificationKeyResolver keys) {
    this.consumer = build(issuer, clientId, keys);
  }

  @PostConstruct
  void init() {
    if (config.enabled()) {
      consumer = build(config.issuer(), config.clientId(),
          new HttpsJwksVerificationKeyResolver(new HttpsJwks(config.jwksUrl())));
    }
  }

  private static JwtConsumer build(String issuer, String clientId, VerificationKeyResolver keys) {
    return new JwtConsumerBuilder()
        .setRequireExpirationTime()
        .setRequireSubject()
        .setAllowedClockSkewInSeconds(CLOCK_SKEW_SECONDS)
        .setExpectedIssuer(issuer)
        .setExpectedAudience(clientId)
        .setVerificationKeyResolver(keys)
        .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT,
            AlgorithmIdentifiers.RSA_USING_SHA256)
        .build();
  }

  /** @return the signed-in user, or {@code null} when the token is not a valid Entra ID token. */
  public EntraUser verify(String token) {
    if (consumer == null || token == null || token.isBlank()) {
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
      LOG.debugf("Entra ID token rejected: %s", e.getMessage());
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
