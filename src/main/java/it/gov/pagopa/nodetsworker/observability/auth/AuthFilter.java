package it.gov.pagopa.nodetsworker.observability.auth;

import it.gov.pagopa.nodetsworker.observability.exception.Errors;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Reads the caller identity from {@code Authorization: Bearer <Entra ID token>} on every {@code /api/*}
 * operation (SSE included), except CORS preflight. Health, info and OpenAPI live outside {@code /api}.
 * The token is validated by Azure API Management; here it is only decoded.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

  private static final String BEARER = "Bearer ";

  @Inject
  AuthConfig config;

  @Inject
  EntraIdTokenReader tokenReader;

  @Inject
  AuthenticatedUser authenticatedUser;

  @Override
  public void filter(ContainerRequestContext request) {
    if (!config.enabled() || HttpMethod.OPTIONS.equals(request.getMethod())
        || !isProtected(request.getUriInfo().getPath())) {
      return;
    }
    String header = request.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
      request.abortWith(unauthorized("Autenticazione richiesta."));
      return;
    }
    EntraUser user = tokenReader.read(header.substring(BEARER.length()).trim());
    if (user == null) {
      request.abortWith(unauthorized("Token non valido."));
      return;
    }
    authenticatedUser.set(user);
  }

  static boolean isProtected(String rawPath) {
    String path = rawPath.startsWith("/") ? rawPath : "/" + rawPath;
    if (path.length() > 1 && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path.equals("/api") || path.startsWith("/api/");
  }

  private static Response unauthorized(String message) {
    return Response.status(401)
        .type("application/json; charset=utf-8")
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .header("Cache-Control", "no-store")
        .entity(Errors.unauthorized(message).toBody())
        .build();
  }
}
