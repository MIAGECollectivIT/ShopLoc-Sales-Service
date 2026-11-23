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
import java.util.UUID;
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

  public static final String STOCK_UPDATE_PATH =
      "/v1/stores/{storeUid}/products/{productUid}/stock";
  private static final String BEARER_PREFIX = "Bearer ";

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

  public fr.miage.collectivit.salesservice.dto.itinerary.OptimizeItineraryResponse
      optimizeItinerary(
          fr.miage.collectivit.salesservice.dto.itinerary.OptimizeItineraryRequest request) {
    String token =
        serviceTokenProvider != null ? serviceTokenProvider.generateServiceToken() : null;
    try {
      var requestBuilder = restClient.post().uri("/v1/stores/itinerary/optimize").body(request);
      if (token != null && !token.isBlank()) {
        requestBuilder.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token);
      }
      return requestBuilder
          .retrieve()
          .body(fr.miage.collectivit.salesservice.dto.itinerary.OptimizeItineraryResponse.class);
    } catch (RestClientException ex) {
      if (log.isErrorEnabled()) {
        log.error("Failed to call Store Service optimize itinerary: {}", ex.getMessage());
      }
      throw new StoreServiceUnavailableException(
          "Impossible de joindre le service des commerces pour le calcul d'itinéraire", ex);
    }
  }

  public void updateStock(UUID storeUid, UUID productUid, int quantityDelta) {
    String token =
        serviceTokenProvider != null ? serviceTokenProvider.generateServiceToken() : null;
    try {
      executeStockPatch(storeUid, productUid, quantityDelta, token);
      logSuccess(storeUid, productUid, quantityDelta);
    } catch (HttpClientErrorException.BadRequest ex) {
      handleBadRequest(storeUid, productUid, ex);
    } catch (RestClientException ex) {
      handleClientException(storeUid, productUid, ex);
    }
  }

  public Optional<java.math.BigDecimal> getProductPrice(UUID storeUid, UUID productUid) {
    String token =
        serviceTokenProvider != null ? serviceTokenProvider.generateServiceToken() : null;
    try {
      var requestBuilder =
          restClient.get().uri("/v1/stores/{storeUid}/products/{productUid}", storeUid, productUid);
      if (token != null && !token.isBlank()) {
        requestBuilder.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token);
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
            storeUid,
            productUid,
            ex.getMessage());
      }
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  public Set<UUID> getMerchantStoreUids(String rawBearerToken) {
    if (rawBearerToken == null || rawBearerToken.isBlank()) {
      return Set.of();
    }
    try {
      List<Map<String, Object>> stores =
          restClient
              .get()
              .uri("/v1/stores/me")
              .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + rawBearerToken.trim())
              .retrieve()
              .body(List.class);
      if (stores == null || stores.isEmpty()) {
        return Set.of();
      }
      Set<UUID> storeUids = new HashSet<>();
      for (Map<String, Object> store : stores) {
        extractStoreUid(store).ifPresent(storeUids::add);
      }
      return storeUids;
    } catch (RestClientException ex) {
      if (log.isWarnEnabled()) {
        log.warn("Failed to fetch merchant store UIDs from Store Service: {}", ex.getMessage());
      }
      return Set.of();
    }
  }

  private Optional<UUID> extractStoreUid(Map<String, Object> store) {
    Object uidObj = store != null ? store.get("uid") : null;
    if (uidObj instanceof UUID uuid) {
      return Optional.of(uuid);
    }
    if (uidObj == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(uidObj.toString().trim()));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  private void executeStockPatch(UUID storeUid, UUID productUid, int quantityDelta, String token) {
    var requestBuilder =
        restClient
            .patch()
            .uri(STOCK_UPDATE_PATH, storeUid, productUid)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("quantityDelta", quantityDelta));

    if (token != null && !token.isBlank()) {
      requestBuilder.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token);
    }

    requestBuilder.retrieve().toBodilessEntity();
  }

  private void logSuccess(UUID storeUid, UUID productUid, int quantityDelta) {
    if (log.isInfoEnabled()) {
      log.info(
          "Stock updated on Store Service: store={}, product={}, delta={}",
          storeUid,
          productUid,
          quantityDelta);
    }
  }

  private void handleBadRequest(
      UUID storeUid, UUID productUid, HttpClientErrorException.BadRequest ex) {
    if (log.isWarnEnabled()) {
      log.warn(
          "Insufficient stock on store {} product {}: {}",
          storeUid,
          productUid,
          ex.getResponseBodyAsString());
    }
    throw new InsufficientStockException(
        "Stock insuffisant pour le produit " + productUid + " auprès du commerce " + storeUid, ex);
  }

  private void handleClientException(UUID storeUid, UUID productUid, RestClientException ex) {
    if (log.isErrorEnabled()) {
      log.error(
          "Failed to communicate with Store Service for store {} product {}: {}",
          storeUid,
          productUid,
          ex.getMessage());
    }
    throw new StoreServiceUnavailableException(
        "Impossible de joindre le service des commerces pour la réservation du stock", ex);
  }
}
