package fr.miage.collectivit.salesservice.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings("PMD.LawOfDemeter")
public class TokenRevocationService {

  private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
  private static final int HTTP_STATUS_OK = 200;
  private static final int HTTP_TIMEOUT_SECONDS = 2;
  private static final long REVOCATION_CACHE_TTL_MS = 15_000L;
  private static final String REVOKED_TOKENS_ENDPOINT = "/v1/auth/revoked-tokens";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Set<String> cachedRevokedJtis =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private volatile long lastRevocationFetchTime;

  @Value("${services.users.url:http://users-service:8080}")
  private String usersServiceUrl;

  public boolean isRevoked(String jti) {
    long now = System.currentTimeMillis();
    if (now - lastRevocationFetchTime > REVOCATION_CACHE_TTL_MS) {
      refreshRevokedTokens();
    }
    return cachedRevokedJtis.contains(jti);
  }

  public synchronized void refreshRevokedTokens() {
    if (usersServiceUrl == null || usersServiceUrl.isBlank()) {
      return;
    }
    lastRevocationFetchTime = System.currentTimeMillis();
    String endpoint = usersServiceUrl.replaceAll("/+$", "") + REVOKED_TOKENS_ENDPOINT;
    fetchRevokedTokensBody(endpoint).ifPresent(this::updateRevokedTokens);
  }

  private void updateRevokedTokens(String responseBody) {
    Set<String> newRevoked = parseRevokedTokens(responseBody);
    cachedRevokedJtis.clear();
    cachedRevokedJtis.addAll(newRevoked);
    if (log.isDebugEnabled()) {
      log.debug(
          "Liste des jetons révoqués rafraîchie : {} jetons actifs.",
          Integer.valueOf(cachedRevokedJtis.size()));
    }
  }

  private Set<String> parseRevokedTokens(String json) {
    Set<String> result = new HashSet<>();
    try {
      JsonNode root = objectMapper.readTree(json);
      if (root.isArray()) {
        for (JsonNode item : root) {
          result.add(item.asText());
        }
      }
    } catch (IOException ex) {
      if (log.isDebugEnabled()) {
        log.debug("Impossible d'analyser les jetons révoqués : {}", ex.getMessage());
      }
    }
    return result;
  }

  private java.util.Optional<String> fetchRevokedTokensBody(String endpoint) {
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
        return java.util.Optional.of(response.body());
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      if (log.isDebugEnabled()) {
        log.debug("Interruption lors de la récupération des jetons révoqués : {}", ex.getMessage());
      }
    } catch (IOException | IllegalArgumentException ex) {
      if (log.isDebugEnabled()) {
        log.debug("Impossible de rafraîchir la liste des jetons révoqués : {}", ex.getMessage());
      }
    }
    return java.util.Optional.empty();
  }

  public void addRevokedJtiForTest(String jti) {
    cachedRevokedJtis.add(jti);
  }
}
