package fr.miage.collectivit.salesservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

  public static final String AUTH_HEADER = "Authorization";
  public static final String TOKEN_PREFIX = "Bearer ";

  public static final String ATTR_USER_ID = "authenticatedUserId";
  public static final String ATTR_USER_DB_ID = "authenticatedUserDbId";
  public static final String ATTR_USER_EMAIL = "authenticatedUserEmail";
  public static final String ATTR_USER_ROLE = "authenticatedUserRole";
  public static final String ATTR_CLAIMS = "jwtClaims";
  public static final String ATTR_RAW_TOKEN = "rawJwtToken";

  private static final String METHOD_OPTIONS = "OPTIONS";
  private static final Set<String> PUBLIC_EXACT_PATHS = Set.of("/swagger-ui.html", "/error");
  private static final String DOCS_PREFIX = "/v3/api-docs";
  private static final String SWAGGER_PREFIX = "/swagger-ui";
  private static final String ACTUATOR_PREFIX = "/actuator";

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

    RequiresRole requiresRole = extractRequiresRole(handler);
    Optional<String> tokenOpt = extractBearerToken(request);

    if (tokenOpt.isEmpty()) {
      return handleMissingToken(requiresRole, response);
    }

    String token = tokenOpt.get();
    Claims claims;
    try {
      claims = jwtValidatorService.validateAndExtractClaims(token);
    } catch (JwtException | IllegalArgumentException | SecurityException ex) {
      return handleInvalidToken(requiresRole, response, path, ex);
    }

    String userRole = jwtValidatorService.extractRole(claims);
    populateRequestAttributes(request, claims, userRole, token);

    return checkRoleAuthorization(requiresRole, userRole, response);
  }

  private RequiresRole extractRequiresRole(Object handler) {
    if (handler instanceof HandlerMethod handlerMethod) {
      RequiresRole methodAnnotation = handlerMethod.getMethodAnnotation(RequiresRole.class);
      if (methodAnnotation != null) {
        return methodAnnotation;
      }
      return handlerMethod.getBeanType().getAnnotation(RequiresRole.class);
    }
    return null;
  }

  private Optional<String> extractBearerToken(HttpServletRequest request) {
    String authHeader = request.getHeader(AUTH_HEADER);
    if (authHeader == null
        || !authHeader.regionMatches(true, 0, TOKEN_PREFIX, 0, TOKEN_PREFIX.length())) {
      return Optional.empty();
    }
    String token = authHeader.substring(TOKEN_PREFIX.length()).trim();
    return token.isEmpty() ? Optional.empty() : Optional.of(token);
  }

  private boolean handleMissingToken(RequiresRole requiresRole, HttpServletResponse response)
      throws IOException {
    if (requiresRole != null) {
      response.sendError(
          HttpServletResponse.SC_UNAUTHORIZED,
          "Missing or invalid authorization header. A JWT Bearer token is required.");
      return false;
    }
    return true;
  }

  private boolean handleInvalidToken(
      RequiresRole requiresRole, HttpServletResponse response, String path, Exception ex)
      throws IOException {
    if (requiresRole != null) {
      response.sendError(
          HttpServletResponse.SC_UNAUTHORIZED,
          "Invalid or expired authentication token: " + ex.getMessage());
      return false;
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "Invalid token on public endpoint {}. Anonymous access allowed: {}",
          path,
          ex.getMessage());
    }
    return true;
  }

  private void populateRequestAttributes(
      HttpServletRequest request, Claims claims, String userRole, String token) {
    request.setAttribute(ATTR_USER_ID, claims.get("userId", String.class));
    Object dbIdObj = claims.get("dbId");
    if (dbIdObj instanceof Number num) {
      request.setAttribute(ATTR_USER_DB_ID, num.longValue());
    } else if (dbIdObj != null) {
      parseAndSetDbId(request, dbIdObj.toString());
    }
    request.setAttribute(ATTR_USER_EMAIL, claims.get("email", String.class));
    request.setAttribute(ATTR_USER_ROLE, userRole);
    request.setAttribute(ATTR_CLAIMS, claims);
    request.setAttribute(ATTR_RAW_TOKEN, token);
  }

  private void parseAndSetDbId(HttpServletRequest request, String dbIdStr) {
    try {
      request.setAttribute(ATTR_USER_DB_ID, Long.parseLong(dbIdStr));
    } catch (NumberFormatException ignored) {
      // Ignored if format does not match
    }
  }

  private boolean checkRoleAuthorization(
      RequiresRole requiresRole, String userRole, HttpServletResponse response) throws IOException {
    if (requiresRole == null) {
      return true;
    }
    UserRole[] allowedRoles = requiresRole.value();
    if (allowedRoles == null || allowedRoles.length == 0) {
      return true;
    }
    for (UserRole allowed : allowedRoles) {
      if (allowed != null && allowed.name().equalsIgnoreCase(userRole)) {
        return true;
      }
    }
    response.sendError(
        HttpServletResponse.SC_FORBIDDEN,
        "Access denied: one of the roles "
            + Arrays.toString(allowedRoles)
            + " is required to perform this action.");
    return false;
  }

  private boolean isWhitelisted(String path) {
    if (PUBLIC_EXACT_PATHS.contains(path)) {
      return true;
    }
    return path.startsWith(DOCS_PREFIX)
        || path.startsWith(SWAGGER_PREFIX)
        || path.startsWith(ACTUATOR_PREFIX);
  }
}
