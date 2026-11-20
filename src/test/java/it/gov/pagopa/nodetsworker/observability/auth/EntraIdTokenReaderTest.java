package it.gov.pagopa.nodetsworker.observability.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntraIdTokenReaderTest {

  private static final String TENANT = "7788edaf-0346-4068-9d79-c868aed15b3d";
  private static RsaJsonWebKey key;
  private static final EntraIdTokenReader reader = new EntraIdTokenReader();

  @BeforeAll
  static void keys() throws Exception {
    key = RsaJwkGenerator.generateJwk(2048);
    key.setKeyId("entra-kid");
  }

  @Test
  void readsIdentityFromToken() throws Exception {
    EntraUser user = reader.read(sign(claims()));
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
    EntraUser user = reader.read(sign(claims));
    assertEquals("mario.rossi@pagopa.onmicrosoft.com", user.email());
    assertEquals("mario.rossi@pagopa.onmicrosoft.com", user.name());
  }

  @Test
  void doesNotValidateSignatureIssuerAudienceOrExpiry() throws Exception {
    JwtClaims claims = claims();
    claims.setIssuer("https://login.microsoftonline.com/other-tenant/v2.0");
    claims.setAudience("another-client");
    NumericDate past = NumericDate.now();
    past.addSeconds(-3600);
    claims.setExpirationTime(past);
    String token = sign(claims);
    String unsigned = token.substring(0, token.lastIndexOf('.') + 1) + "forged-signature";
    assertEquals("oid-1", reader.read(unsigned).oid());
  }

  @Test
  void rejectsMissingObjectId() throws Exception {
    JwtClaims claims = claims();
    claims.unsetClaim("oid");
    assertNull(reader.read(sign(claims)));
  }

  @Test
  void rejectsGarbageAndBlank() {
    assertNull(reader.read("not-a-jwt"));
    assertNull(reader.read(" "));
    assertNull(reader.read(null));
  }

  private static JwtClaims claims() {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer("https://login.microsoftonline.com/" + TENANT + "/v2.0");
    claims.setAudience("11111111-2222-3333-4444-555555555555");
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

  private static String sign(JwtClaims claims) throws Exception {
    JsonWebSignature jws = new JsonWebSignature();
    jws.setPayload(claims.toJson());
    jws.setKey(key.getPrivateKey());
    jws.setKeyIdHeaderValue(key.getKeyId());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    return jws.getCompactSerialization();
  }
}
