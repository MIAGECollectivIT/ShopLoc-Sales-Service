package fr.miage.collectivit.salesservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("JwtAuthInterceptor Unit Tests")
class JwtAuthInterceptorTest {

  @Test
  @DisplayName(
      "Should populate UUID userId and never store authenticatedUserDbId even if dbId present")
  void shouldIgnoreDbIdClaimAndPopulateUserId() throws Exception {
    JwtValidatorService validator = mock(JwtValidatorService.class);
    JwtAuthInterceptor interceptor = new JwtAuthInterceptor(validator);

    UUID userUid = UUID.randomUUID();
    Claims claims =
        Jwts.claims()
            .subject(userUid.toString())
            .add("userId", userUid.toString())
            .add("dbId", 42L)
            .add("email", "client@shoploc.fr")
            .add("role", "CLIENT")
            .build();

    when(validator.validateAndExtractClaims("test-jwt")).thenReturn(claims);
    when(validator.extractRole(claims)).thenReturn("CLIENT");

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/sales/orders");
    request.addHeader("Authorization", "Bearer test-jwt");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isTrue();
    assertThat(request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID)).isEqualTo(userUid.toString());
    assertThat(request.getAttribute("authenticatedUserDbId")).isNull();
  }
}
