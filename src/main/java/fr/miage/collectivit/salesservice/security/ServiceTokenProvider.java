package fr.miage.collectivit.salesservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Generates internal machine-to-machine (M2M) JWT tokens with role SERVICE for inter-service calls.
 */
@Slf4j
@Component
public class ServiceTokenProvider {

  private static final long TOKEN_VALIDITY_MS = 300_000L;
  private static final int MIN_KEY_BYTES = 32;

  @Value("${application.security.jwt.private-key:}")
  private String privateKeyString;

  @Value("${application.security.jwt.secret-key:}")
  private String secretKeyString;

  private PrivateKey privateKey;
  private SecretKey secretKey;

  @PostConstruct
  public void init() {
    if (privateKeyString != null && !privateKeyString.isBlank()) {
      parseRsaPrivateKey(privateKeyString);
    }
    if (secretKeyString != null && !secretKeyString.isBlank()) {
      parseHmacSecretKey(secretKeyString);
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

  private void parseHmacSecretKey(String rawKey) {
    try {
      String trimmed = rawKey.trim();
      byte[] keyBytes;
      try {
        keyBytes = Base64.getDecoder().decode(trimmed);
        if (keyBytes.length < MIN_KEY_BYTES) {
          keyBytes = trimmed.getBytes(StandardCharsets.UTF_8);
        }
      } catch (IllegalArgumentException notBase64) {
        keyBytes = trimmed.getBytes(StandardCharsets.UTF_8);
      }
      this.secretKey = Keys.hmacShaKeyFor(keyBytes);
      if (log.isInfoEnabled()) {
        log.info("ServiceTokenProvider initialized with HMAC secret key.");
      }
    } catch (IllegalArgumentException ex) {
      if (log.isWarnEnabled()) {
        log.warn("Unable to initialize HMAC key for ServiceTokenProvider: {}", ex.getMessage());
      }
    }
  }

  public String generateServiceToken() {
    long now = System.currentTimeMillis();
    var builder =
        Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject("sales-service")
            .issuer("shoploc-sales-service")
            .issuedAt(new Date(now))
            .expiration(new Date(now + TOKEN_VALIDITY_MS))
            .claim("userId", "sales-service")
            .claim("email", "sales-service@shoploc.internal")
            .claim("role", "SERVICE");

    if (privateKey != null) {
      return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }
    if (secretKey != null) {
      return builder.signWith(secretKey).compact();
    }
    if (log.isWarnEnabled()) {
      log.warn("No signing key configured in ServiceTokenProvider; returning empty token");
    }
    return "";
  }
}
