package fr.miage.collectivit.salesservice.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@SuppressWarnings("PMD.LawOfDemeter")
public class JwtValidatorService {

  private static final String ROLE_CLAIM = "role";
  private static final String USER_ROLE_CLAIM = "userRole";
  private static final String EMPTY_ROLE = "";

  private final JwtKeyProvider jwtKeyProvider;
  private final TokenRevocationService tokenRevocationService;

  public Claims validateAndExtractClaims(String token) {
    Claims claims = jwtKeyProvider.parseSignedClaims(token);
    verifyNotRevoked(claims);
    return claims;
  }

  private void verifyNotRevoked(Claims claims) {
    String jti = claims.getId();
    if (jti != null && tokenRevocationService.isRevoked(jti)) {
      throw new SecurityException("JWT token revoked. Please log in again.");
    }
  }

  public String extractRole(Claims claims) {
    String role = claims.get(ROLE_CLAIM, String.class);
    if (role == null) {
      role = claims.get(USER_ROLE_CLAIM, String.class);
    }
    return role != null ? role.trim() : EMPTY_ROLE;
  }

  public void addRevokedJtiForTest(String jti) {
    tokenRevocationService.addRevokedJtiForTest(jti);
  }
}
