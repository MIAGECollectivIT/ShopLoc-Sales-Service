package fr.miage.collectivit.salesservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

  public static final String AUTH_HEADER = "Authorization";
  public static final String TOKEN_PREFIX = "Bearer ";

  public static final String ATTR_USER_ID = "authenticatedUserId";
  public static final String ATTR_USER_EMAIL = "authenticatedUserEmail";
  public static final String ATTR_USER_ROLE = "authenticatedUserRole";
  public static final String ATTR_CLAIMS = "jwtClaims";

  private static final String METHOD_OPTIONS = "OPTIONS";
  private static final Set<String> PUBLIC_EXACT_PATHS = Set.of("/swagger-ui.html", "/error");
  private static final String DOCS_PREFIX = "/v3/api-docs";
  private static final String SWAGGER_PREFIX = "/swagger-ui";
  private static final String ACTUATOR_PREFIX = "/actuator";
  private static final String PAYMENTS_PREFIX = "/v1/sales/payments";
  private static final String ORDERS_PREFIX = "/v1/sales/orders";

  private final JwtValidatorService jwtValidatorService;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    if (METHOD_OPTIONS.equalsIgnoreCase(request.getMethod())) {
      return true;
    }

    String path = request.getRequestURI();
    if (isWhitelisted(path)) {
      return true;
    }

    String authHeader = request.getHeader(AUTH_HEADER);
    boolean hasBearerHeader =
        authHeader != null
            && authHeader.regionMatches(true, 0, TOKEN_PREFIX, 0, TOKEN_PREFIX.length());

    if (!hasBearerHeader) {
      if (isOptionalAuthPath(path)) {
        return true;
      }
      response.sendError(
          HttpServletResponse.SC_UNAUTHORIZED,
          "Missing or invalid Authorization header. A Bearer JWT token is required.");
      return false;
    }

    String token = authHeader.substring(TOKEN_PREFIX.length()).trim();
    if (token.isEmpty()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Empty or missing JWT token.");
      return false;
    }

    try {
      Claims claims = jwtValidatorService.validateAndExtractClaims(token);
      request.setAttribute(ATTR_USER_ID, claims.get("userId", String.class));
      request.setAttribute(ATTR_USER_EMAIL, claims.get("email", String.class));
      request.setAttribute(ATTR_USER_ROLE, jwtValidatorService.extractRole(claims));
      request.setAttribute(ATTR_CLAIMS, claims);
      return true;
    } catch (JwtException | IllegalArgumentException | SecurityException ex) {
      response.sendError(
          HttpServletResponse.SC_UNAUTHORIZED,
          "Invalid or expired authentication token: " + ex.getMessage());
      return false;
    }
  }

  private boolean isWhitelisted(String path) {
    if (PUBLIC_EXACT_PATHS.contains(path)) {
      return true;
    }
    return path.startsWith(DOCS_PREFIX)
        || path.startsWith(SWAGGER_PREFIX)
        || path.startsWith(ACTUATOR_PREFIX);
  }

  private boolean isOptionalAuthPath(String path) {
    return path.startsWith(PAYMENTS_PREFIX) || path.startsWith(ORDERS_PREFIX);
  }
}
