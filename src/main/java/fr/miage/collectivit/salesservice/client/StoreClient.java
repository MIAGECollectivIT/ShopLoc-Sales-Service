package fr.miage.collectivit.salesservice.client;

import fr.miage.collectivit.salesservice.exception.InsufficientStockException;
import fr.miage.collectivit.salesservice.exception.StoreServiceUnavailableException;
import fr.miage.collectivit.salesservice.security.ServiceTokenProvider;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP REST client communicating with Store Service to reserve and release product stocks (Saga).
 */
@Slf4j
@Setter
@Component
public class StoreClient {

  public static final String STOCK_UPDATE_PATH = "/v1/stores/{storeId}/products/{productId}/stock";

  private final ServiceTokenProvider serviceTokenProvider;
  private RestClient restClient;

  @Autowired
  public StoreClient(
      ServiceTokenProvider serviceTokenProvider,
      @Value("${services.store.url:http://store-service:8080}") String storeServiceUrl) {
    this.serviceTokenProvider = serviceTokenProvider;
    this.restClient =
        RestClient.builder()
            .baseUrl(storeServiceUrl)
            .requestFactory(createClientHttpRequestFactory())
            .build();
  }

  @SuppressWarnings("PMD.CloseResource")
  private static ClientHttpRequestFactory createClientHttpRequestFactory() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofSeconds(5));
    return factory;
  }

  public void updateStock(Long storeId, Long productId, int quantityDelta) {
    String token =
        serviceTokenProvider != null ? serviceTokenProvider.generateServiceToken() : null;
    try {
      executeStockPatch(storeId, productId, quantityDelta, token);
      logSuccess(storeId, productId, quantityDelta);
    } catch (HttpClientErrorException.BadRequest ex) {
      handleBadRequest(storeId, productId, ex);
    } catch (RestClientException ex) {
      handleClientException(storeId, productId, ex);
    }
  }

  public Optional<java.math.BigDecimal> getProductPrice(Long storeId, Long productId) {
    String token =
        serviceTokenProvider != null ? serviceTokenProvider.generateServiceToken() : null;
    try {
      var requestBuilder =
          restClient.get().uri("/v1/stores/{storeId}/products/{productId}", storeId, productId);
      if (token != null && !token.isBlank()) {
        requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      }
      Map<?, ?> response = requestBuilder.retrieve().body(Map.class);
      if (response != null && response.get("price") != null) {
        return Optional.of(new java.math.BigDecimal(response.get("price").toString()));
      }
      return Optional.empty();
    } catch (RestClientException ex) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Could not fetch product price from Store Service for store {} product {}: {}",
            storeId,
            productId,
            ex.getMessage());
      }
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  public Set<Long> getMerchantStoreIds(String rawBearerToken) {
    if (rawBearerToken == null || rawBearerToken.isBlank()) {
      return Set.of();
    }
    try {
      List<Map<String, Object>> stores =
          restClient
              .get()
              .uri("/v1/stores/me")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawBearerToken.trim())
              .retrieve()
              .body(List.class);
      if (stores == null || stores.isEmpty()) {
        return Set.of();
      }
      Set<Long> storeIds = new HashSet<>();
      for (Map<String, Object> store : stores) {
        extractStoreId(store).ifPresent(storeIds::add);
      }
      return storeIds;
    } catch (RestClientException ex) {
      if (log.isWarnEnabled()) {
        log.warn("Failed to fetch merchant store IDs from Store Service: {}", ex.getMessage());
      }
      return Set.of();
    }
  }

  private Optional<Long> extractStoreId(Map<String, Object> store) {
    Object idObj = store != null ? store.get("id") : null;
    if (idObj instanceof Number num) {
      return Optional.of(num.longValue());
    }
    if (idObj == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(idObj.toString()));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  private void executeStockPatch(Long storeId, Long productId, int quantityDelta, String token) {
    var requestBuilder =
        restClient
            .patch()
            .uri(STOCK_UPDATE_PATH, storeId, productId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("quantityDelta", quantityDelta));

    if (token != null && !token.isBlank()) {
      requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    requestBuilder.retrieve().toBodilessEntity();
  }

  private void logSuccess(Long storeId, Long productId, int quantityDelta) {
    if (log.isInfoEnabled()) {
      log.info(
          "Stock updated on Store Service: store={}, product={}, delta={}",
          storeId,
          productId,
          quantityDelta);
    }
  }

  private void handleBadRequest(
      Long storeId, Long productId, HttpClientErrorException.BadRequest ex) {
    if (log.isWarnEnabled()) {
      log.warn(
          "Insufficient stock on store {} product {}: {}",
          storeId,
          productId,
          ex.getResponseBodyAsString());
    }
    throw new InsufficientStockException(
        "Stock insuffisant pour le produit #" + productId + " auprès du commerce #" + storeId, ex);
  }

  private void handleClientException(Long storeId, Long productId, RestClientException ex) {
    if (log.isErrorEnabled()) {
      log.error(
          "Failed to communicate with Store Service for store {} product {}: {}",
          storeId,
          productId,
          ex.getMessage());
    }
    throw new StoreServiceUnavailableException(
        "Impossible de joindre le service des commerces pour la réservation du stock", ex);
  }
}
