package fr.miage.collectivit.salesservice.security;

import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Generates internal machine-to-machine (M2M) JWT tokens with role SERVICE for inter-service calls.
 * Uses asymmetric RS256 signature only.
 */
@Slf4j
@Component
public class ServiceTokenProvider {

  private static final long TOKEN_VALIDITY_MS = 300_000L;
  private static final long TOKEN_REFRESH_MARGIN_MS = 30_000L;

  @Value("${application.security.jwt.private-key:}")
  private String privateKeyString;

  private PrivateKey privateKey;
  private volatile String cachedToken;
  private volatile long cachedTokenExpiresAt;

  @PostConstruct
  public void init() {
    if (privateKeyString != null && !privateKeyString.isBlank()) {
      parseRsaPrivateKey(privateKeyString);
    }
  }

  private void parseRsaPrivateKey(String rawKey) {
    try {
      String clean =
          rawKey
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s+", "");
      byte[] decoded = Base64.getDecoder().decode(clean);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      this.privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
      if (log.isInfoEnabled()) {
        log.info("ServiceTokenProvider initialized with RSA private key (RS256).");
      }
    } catch (IllegalArgumentException | java.security.GeneralSecurityException ex) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Unable to initialize RSA private key for ServiceTokenProvider: {}", ex.getMessage());
      }
    }
  }

  public synchronized String generateServiceToken() {
    long now = System.currentTimeMillis();
    if (cachedToken != null && now + TOKEN_REFRESH_MARGIN_MS < cachedTokenExpiresAt) {
      return cachedToken;
    }

    long expiresAt = now + TOKEN_VALIDITY_MS;
    var builder =
        Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject("sales-service")
            .issuer("shoploc-sales-service")
            .issuedAt(new Date(now))
            .expiration(new Date(expiresAt))
            .claim("userId", "sales-service")
            .claim("email", "sales-service@shoploc.internal")
            .claim("role", "SERVICE");

    if (privateKey != null) {
      cachedToken = builder.signWith(privateKey, Jwts.SIG.RS256).compact();
      cachedTokenExpiresAt = expiresAt;
      return cachedToken;
    }
    if (log.isWarnEnabled()) {
      log.warn("No RSA private key configured in ServiceTokenProvider; returning empty token");
    }
    return "";
  }
}
