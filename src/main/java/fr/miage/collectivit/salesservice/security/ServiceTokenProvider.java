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

  @Value("${application.security.jwt.private-key:}")
  private String privateKeyString;

  private PrivateKey privateKey;

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
    if (log.isWarnEnabled()) {
      log.warn("No RSA private key configured in ServiceTokenProvider; returning empty token");
    }
    return "";
  }
}
