package fr.miage.collectivit.salesservice.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("PMD.LawOfDemeter")
public class JwtKeyProvider {

  private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);
  private static final int MIN_KEY_BYTES = 32;
  private static final int MIN_RSA_KEY_BYTES = 200;
  private static final int HTTP_STATUS_OK = 200;
  private static final int HTTP_TIMEOUT_SECONDS = 2;
  private static final String PROPERTY_PUBLIC_KEY = "publicKey";
  private static final String RSA_ALGORITHM = "RSA";
  private static final String PEM_HEADER = "-----BEGIN PUBLIC KEY-----";
  private static final String PEM_FOOTER = "-----END PUBLIC KEY-----";
  private static final String WHITESPACE_REGEX = "\\s+";
  private static final String EMPTY_STRING = "";
  private static final String PUBLIC_KEY_ENDPOINT = "/v1/auth/public-key";

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${application.security.jwt.public-key:}")
  private String publicKeyString;

  @Value("${application.security.jwt.secret-key:}")
  private String secretKeyString;

  @Value("${services.users.url:http://users-service:8080}")
  private String usersServiceUrl;

  private JwtParser rsaParser;
  private JwtParser hmacParser;

  @PostConstruct
  public void init() {
    if (publicKeyString != null && !publicKeyString.isBlank()) {
      parseRsaPublicKey(publicKeyString);
    }

    if (secretKeyString != null && !secretKeyString.isBlank()) {
      parseHmacSecretKey(secretKeyString);
    }

    if (this.rsaParser == null && this.hmacParser == null) {
      fetchPublicKeyFromUsersService();
    }
  }

  private void parseRsaPublicKey(String rawKey) {
    try {
      String clean =
          rawKey
              .replace(PEM_HEADER, EMPTY_STRING)
              .replace(PEM_FOOTER, EMPTY_STRING)
              .replaceAll(WHITESPACE_REGEX, EMPTY_STRING);
      byte[] decoded = Base64.getDecoder().decode(clean);
      if (decoded.length > MIN_RSA_KEY_BYTES) {
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        this.rsaParser = Jwts.parser().verifyWith(publicKey).build();
        if (log.isInfoEnabled()) {
          log.info("JWT Validator initialized with asymmetric RSA public key (RS256).");
        }
      }
    } catch (IllegalArgumentException | GeneralSecurityException ex) {
      if (log.isWarnEnabled()) {
        log.warn("Unable to initialize RSA public key: {}", ex.getMessage());
      }
    }
  }

  private void parseHmacSecretKey(String rawKey) {
    try {
      byte[] keyBytes = extractHmacBytes(rawKey);
      SecretKey secretKey = Keys.hmacShaKeyFor(keyBytes);
      this.hmacParser = Jwts.parser().verifyWith(secretKey).build();
      if (log.isInfoEnabled()) {
        log.info("JWT Validator initialized with HMAC symmetric key support.");
      }
    } catch (IllegalArgumentException ex) {
      if (log.isWarnEnabled()) {
        log.warn("Unable to initialize HMAC key: {}", ex.getMessage());
      }
    }
  }

  private byte[] extractHmacBytes(String rawKey) {
    String trimmed = rawKey.trim();
    try {
      byte[] keyBytes = Base64.getDecoder().decode(trimmed);
      if (keyBytes.length >= MIN_KEY_BYTES) {
        return keyBytes;
      }
      return trimmed.getBytes(StandardCharsets.UTF_8);
    } catch (IllegalArgumentException notBase64) {
      return trimmed.getBytes(StandardCharsets.UTF_8);
    }
  }

  public synchronized void fetchPublicKeyFromUsersService() {
    if (usersServiceUrl == null || usersServiceUrl.isBlank()) {
      return;
    }
    String endpoint = usersServiceUrl.replaceAll("/+$", "") + PUBLIC_KEY_ENDPOINT;
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS)).build()) {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(endpoint))
              .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == HTTP_STATUS_OK) {
        parseFetchedKey(response.body(), endpoint);
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      if (log.isInfoEnabled()) {
        log.info("Interrupted while fetching public key: {}", ex.getMessage());
      }
    } catch (IOException | IllegalArgumentException ex) {
      if (log.isInfoEnabled()) {
        log.info("Unable to fetch public key from users-service: {}", ex.getMessage());
      }
    }
  }

  private void parseFetchedKey(String body, String endpoint) throws IOException {
    JsonNode root = objectMapper.readTree(body);
    if (root.has(PROPERTY_PUBLIC_KEY)) {
      parseRsaPublicKey(root.get(PROPERTY_PUBLIC_KEY).asText());
      if (log.isInfoEnabled()) {
        log.info("RSA public key dynamically fetched from {}", endpoint);
      }
    }
  }

  public Claims parseSignedClaims(String token) {
    if (rsaParser == null && hmacParser == null) {
      fetchPublicKeyFromUsersService();
    }

    Optional<Claims> claimsOpt = tryParseWithRsa(token);
    if (claimsOpt.isPresent()) {
      return claimsOpt.get();
    }

    claimsOpt = tryParseWithHmac(token);
    if (claimsOpt.isPresent()) {
      return claimsOpt.get();
    }

    throw new SecurityException("No valid key available to verify JWT token.");
  }

  private Optional<Claims> tryParseWithRsa(String token) {
    if (rsaParser == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(rsaParser.parseSignedClaims(token).getPayload());
    } catch (JwtException | IllegalArgumentException ex) {
      if (hmacParser == null) {
        throw new SecurityException("Invalid or expired JWT token: " + ex.getMessage(), ex);
      }
      return Optional.empty();
    }
  }

  private Optional<Claims> tryParseWithHmac(String token) {
    if (hmacParser == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(hmacParser.parseSignedClaims(token).getPayload());
    } catch (JwtException | IllegalArgumentException ex) {
      throw new SecurityException("Invalid or expired JWT token: " + ex.getMessage(), ex);
    }
  }
}
