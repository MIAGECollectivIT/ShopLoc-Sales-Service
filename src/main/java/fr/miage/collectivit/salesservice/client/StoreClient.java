package fr.miage.collectivit.salesservice.client;

import fr.miage.collectivit.salesservice.exception.InsufficientStockException;
import fr.miage.collectivit.salesservice.exception.StoreServiceUnavailableException;
import fr.miage.collectivit.salesservice.security.ServiceTokenProvider;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
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
    String token = serviceTokenProvider.generateServiceToken();
    try {
      executeStockPatch(storeId, productId, quantityDelta, token);
      logSuccess(storeId, productId, quantityDelta);
    } catch (HttpClientErrorException.BadRequest ex) {
      handleBadRequest(storeId, productId, ex);
    } catch (RestClientException ex) {
      handleClientException(storeId, productId, ex);
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
