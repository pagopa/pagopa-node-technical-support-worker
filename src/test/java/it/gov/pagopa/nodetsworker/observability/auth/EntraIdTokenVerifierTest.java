package it.gov.pagopa.nodetsworker.observability.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntraIdTokenVerifierTest {

  private static final String TENANT = "7788edaf-0346-4068-9d79-c868aed15b3d";
  private static final String ISSUER = "https://login.microsoftonline.com/" + TENANT + "/v2.0";
  private static final String CLIENT_ID = "11111111-2222-3333-4444-555555555555";
  private static RsaJsonWebKey tenantKey;
  private static RsaJsonWebKey otherKey;
  private static EntraIdTokenVerifier verifier;

  @BeforeAll
  static void keys() throws Exception {
    tenantKey = RsaJwkGenerator.generateJwk(2048);
    tenantKey.setKeyId("entra-kid");
    otherKey = RsaJwkGenerator.generateJwk(2048);
    otherKey.setKeyId("entra-kid");
    verifier = new EntraIdTokenVerifier(ISSUER, CLIENT_ID, new JwksVerificationKeyResolver(List.of(tenantKey)));
  }

  @Test
  void acceptsValidTokenAndExtractsIdentity() throws Exception {
    EntraUser user = verifier.verify(sign(claims(), tenantKey));
    assertNotNull(user);
    assertEquals("oid-1", user.oid());
    assertEquals("mario.rossi@pagopa.it", user.email());
    assertEquals("Mario Rossi", user.name());
  }

  @Test
  void fallsBackToPreferredUsernameAndEmailAsName() throws Exception {
    JwtClaims claims = claims();
    claims.unsetClaim("email");
    claims.unsetClaim("name");
    EntraUser user = verifier.verify(sign(claims, tenantKey));
    assertEquals("mario.rossi@pagopa.onmicrosoft.com", user.email());
    assertEquals("mario.rossi@pagopa.onmicrosoft.com", user.name());
  }

  @Test
  void rejectsMissingObjectId() throws Exception {
    JwtClaims claims = claims();
    claims.unsetClaim("oid");
    assertNull(verifier.verify(sign(claims, tenantKey)));
  }

  @Test
  void rejectsWrongAudience() throws Exception {
    JwtClaims claims = claims();
    claims.setAudience("another-client");
    assertNull(verifier.verify(sign(claims, tenantKey)));
  }

  @Test
  void rejectsWrongIssuer() throws Exception {
    JwtClaims otherTenant = claims();
    otherTenant.setIssuer("https://login.microsoftonline.com/other-tenant/v2.0");
    assertNull(verifier.verify(sign(otherTenant, tenantKey)));
    JwtClaims v1 = claims();
    v1.setIssuer("https://sts.windows.net/" + TENANT + "/");
    assertNull(verifier.verify(sign(v1, tenantKey)));
  }

  @Test
  void rejectsExpiredToken() throws Exception {
    JwtClaims claims = claims();
    NumericDate past = NumericDate.now();
    past.addSeconds(-3600);
    claims.setExpirationTime(past);
    assertNull(verifier.verify(sign(claims, tenantKey)));
  }

  @Test
  void rejectsTokenNotYetValid() throws Exception {
    JwtClaims claims = claims();
    NumericDate future = NumericDate.now();
    future.addSeconds(600);
    claims.setNotBefore(future);
    assertNull(verifier.verify(sign(claims, tenantKey)));
  }

  @Test
  void rejectsSignatureFromUnknownKey() throws Exception {
    assertNull(verifier.verify(sign(claims(), otherKey)));
  }

  @Test
  void rejectsGarbageAndBlank() {
    assertNull(verifier.verify("not-a-jwt"));
    assertNull(verifier.verify(" "));
    assertNull(verifier.verify(null));
  }

  private static JwtClaims claims() {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(ISSUER);
    claims.setAudience(CLIENT_ID);
    claims.setSubject("subject-1");
    claims.setIssuedAtToNow();
    claims.setNotBeforeMinutesInThePast(1);
    claims.setExpirationTimeMinutesInTheFuture(10);
    claims.setClaim("tid", TENANT);
    claims.setClaim("oid", "oid-1");
    claims.setClaim("email", "mario.rossi@pagopa.it");
    claims.setClaim("preferred_username", "mario.rossi@pagopa.onmicrosoft.com");
    claims.setClaim("name", "Mario Rossi");
    return claims;
  }

  private static String sign(JwtClaims claims, RsaJsonWebKey key) throws Exception {
    JsonWebSignature jws = new JsonWebSignature();
    jws.setPayload(claims.toJson());
    jws.setKey(key.getPrivateKey());
    jws.setKeyIdHeaderValue(key.getKeyId());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    return jws.getCompactSerialization();
  }
}
