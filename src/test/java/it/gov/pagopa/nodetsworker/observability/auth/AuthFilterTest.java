package it.gov.pagopa.nodetsworker.observability.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.junit.mockito.InjectMock;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Boots the app with Entra ID authentication enabled and checks the /api bearer identity guard. */
@QuarkusTest
@TestProfile(AuthFilterTest.AuthEnabled.class)
class AuthFilterTest {

  public static class AuthEnabled implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("observability.auth.enabled", "true");
    }
  }

  @InjectMock
  EntraIdTokenReader tokenReader;

  @BeforeEach
  void tokens() {
    when(tokenReader.read(anyString())).thenReturn(null);
    when(tokenReader.read("good-token"))
        .thenReturn(new EntraUser("oid-1", "mario.rossi@pagopa.it", "Mario Rossi"));
  }

  @Test
  void sseStreamRequiresBearerToken() {
    given().accept("text/event-stream").when().get("/api/events?scope=overview")
        .then().statusCode(401).body("code", equalTo("UNAUTHORIZED"));
  }

  @Test
  void protectedPathMatching() {
    assertTrue(AuthFilter.isProtected("api/events"));
    assertTrue(AuthFilter.isProtected("/api"));
    assertFalse(AuthFilter.isProtected("/info"));
    assertFalse(AuthFilter.isProtected("/apiconfig"));
    assertFalse(AuthFilter.isProtected("/internal/observability/sync/payment"));
  }
}
