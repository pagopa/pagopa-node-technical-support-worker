package it.gov.pagopa.nodetsworker.observability.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Boots the app with Entra ID authentication enabled and checks the /api bearer guard. */
@QuarkusTest
@TestProfile(AuthFilterTest.AuthEnabled.class)
class AuthFilterTest {

  public static class AuthEnabled implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "observability.auth.enabled", "true",
          "observability.auth.entra.tenant-id", "7788edaf-0346-4068-9d79-c868aed15b3d",
          "observability.auth.entra.client-id", "11111111-2222-3333-4444-555555555555");
    }
  }

  @InjectMock
  EntraIdTokenVerifier tokenVerifier;

  @Inject
  AuthConfig config;

  @BeforeEach
  void tokens() {
    when(tokenVerifier.verify(anyString())).thenReturn(null);
    when(tokenVerifier.verify("good-token"))
        .thenReturn(new EntraUser("oid-1", "mario.rossi@pagopa.it", "Mario Rossi"));
  }

  @Test
  void validTokenAuthenticatesTheCaller() {
    given().header("Authorization", "Bearer good-token")
        .when().get("/api/me")
        .then().statusCode(200)
        .body("id", equalTo("mario.rossi@pagopa.it"))
        .body("role", equalTo("operator"))
        .body("authentication", equalTo("ENTRA_ID"));
  }

  @Test
  void apiRequiresBearerToken() {
    given().when().get("/api/me")
        .then().statusCode(401)
        .header("WWW-Authenticate", "Bearer")
        .header("Cache-Control", "no-store")
        .body("code", equalTo("UNAUTHORIZED"));
    given().header("Authorization", "Basic abc").when().get("/api/catalog")
        .then().statusCode(401).body("code", equalTo("UNAUTHORIZED"));
  }

  @Test
  void apiRejectsInvalidToken() {
    given().header("Authorization", "Bearer expired-or-forged").when().get("/api/me")
        .then().statusCode(401).body("code", equalTo("UNAUTHORIZED"));
  }

  @Test
  void sseStreamRequiresBearerToken() {
    given().accept("text/event-stream").when().get("/api/events?scope=overview")
        .then().statusCode(401).body("code", equalTo("UNAUTHORIZED"));
  }

  @Test
  void corsPreflightAndPublicEndpointsAreNotGuarded() {
    given().header("Origin", "https://observability.dev.platform.pagopa.it")
        .header("Access-Control-Request-Method", "GET")
        .header("Access-Control-Request-Headers", "authorization")
        .when().options("/api/me")
        .then().statusCode(not(equalTo(401)));
    given().when().get("/q/health/live").then().statusCode(200);
    given().when().get("/q/openapi").then().statusCode(200);
  }

  @Test
  void tenantDerivesIssuerAndJwks() {
    assertEquals("https://login.microsoftonline.com/7788edaf-0346-4068-9d79-c868aed15b3d/v2.0", config.issuer());
    assertEquals("https://login.microsoftonline.com/7788edaf-0346-4068-9d79-c868aed15b3d/discovery/v2.0/keys",
        config.jwksUrl());
  }

  @Test
  void protectedPathMatching() {
    assertTrue(AuthFilter.isProtected("/api/me"));
    assertTrue(AuthFilter.isProtected("api/events"));
    assertTrue(AuthFilter.isProtected("/api"));
    assertFalse(AuthFilter.isProtected("/info"));
    assertFalse(AuthFilter.isProtected("/apiconfig"));
    assertFalse(AuthFilter.isProtected("/internal/observability/sync/payment"));
  }
}
