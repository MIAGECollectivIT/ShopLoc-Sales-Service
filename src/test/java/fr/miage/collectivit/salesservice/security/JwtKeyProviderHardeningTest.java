package fr.miage.collectivit.salesservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JwtKeyProvider Hardening Tests (RS256 only, rejection of HMAC algorithm confusion)")
class JwtKeyProviderHardeningTest {

  private JwtKeyProvider jwtKeyProvider;

  @BeforeEach
  void setUp() {
    jwtKeyProvider = new JwtKeyProvider();
    ReflectionTestUtils.setField(jwtKeyProvider, "publicKeyString", TestJwtTokens.TEST_PUBLIC_KEY);
    ReflectionTestUtils.setField(jwtKeyProvider, "usersServiceUrl", "");
    jwtKeyProvider.init();
  }

  @Test
  @DisplayName("Should successfully parse valid RS256 token signed by trusted private key")
  void shouldParseValidRs256Token() {
    String token =
        TestJwtTokens.generateToken(
            "CLIENT", "test@shoploc.fr", UUID.randomUUID().toString(), null, "CL10001");

    Claims claims = jwtKeyProvider.parseSignedClaims(token);
    assertThat(claims).isNotNull();
    assertThat(claims.get("role", String.class)).isEqualTo("CLIENT");
    assertThat(claims.get("email", String.class)).isEqualTo("test@shoploc.fr");
  }

  @Test
  @DisplayName(
      "Should reject token forged with HMAC using public key bytes (prevent algorithm confusion)")
  void shouldRejectHmacForgedToken() {
    SecretKey forgedKey = Keys.hmacShaKeyFor(TestJwtTokens.getPublicKey().getEncoded());
    long now = System.currentTimeMillis();
    String forgedToken =
        Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject("attacker")
            .issuer("shoploc-auth-service")
            .issuedAt(new Date(now))
            .expiration(new Date(now + 3600000))
            .claim("userId", UUID.randomUUID().toString())
            .claim("email", "attacker@evil.com")
            .claim("role", "ADMIN")
            .signWith(forgedKey)
            .compact();

    assertThatThrownBy(() -> jwtKeyProvider.parseSignedClaims(forgedToken))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("Invalid or expired JWT token");
  }

  @Test
  @DisplayName("Should throw SecurityException when no valid key is available")
  void shouldThrowWhenNoKeyAvailable() {
    JwtKeyProvider emptyProvider = new JwtKeyProvider();
    ReflectionTestUtils.setField(emptyProvider, "publicKeyString", "");
    ReflectionTestUtils.setField(emptyProvider, "usersServiceUrl", "");
    emptyProvider.init();

    String token =
        TestJwtTokens.generateToken(
            "CLIENT", "test@shoploc.fr", UUID.randomUUID().toString(), null, "CL10001");

    assertThatThrownBy(() -> emptyProvider.parseSignedClaims(token))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("No valid RSA public key available");
  }
}
