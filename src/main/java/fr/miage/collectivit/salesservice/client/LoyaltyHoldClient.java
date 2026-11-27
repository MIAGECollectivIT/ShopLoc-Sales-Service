package fr.miage.collectivit.salesservice.client;

import fr.miage.collectivit.salesservice.exception.ForbiddenException;
import fr.miage.collectivit.salesservice.security.ServiceTokenProvider;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
 * REST client communicating with Loyalty Service to hold and release gift points/stock (SLE-41).
 */
@Slf4j
@Setter
@Component
public class LoyaltyHoldClient {

  private static final String BEARER_PREFIX = "Bearer ";

  private final ServiceTokenProvider serviceTokenProvider;
  private RestClient restClient;

  @Autowired
  public LoyaltyHoldClient(
      ServiceTokenProvider serviceTokenProvider,
      @Value("${services.loyalty.url:http://loyalty-service:8080}") String loyaltyServiceUrl) {
    this.serviceTokenProvider = serviceTokenProvider;
    this.restClient =
        RestClient.builder()
            .baseUrl(loyaltyServiceUrl)
            .requestFactory(createClientHttpRequestFactory())
            .build();
  }

  public LoyaltyHoldClient(RestClient restClient) {
    this(null, restClient);
  }

  public LoyaltyHoldClient(ServiceTokenProvider serviceTokenProvider, RestClient restClient) {
    this.serviceTokenProvider = serviceTokenProvider;
    this.restClient = restClient;
  }

  @SuppressWarnings("PMD.CloseResource")
  private static ClientHttpRequestFactory createClientHttpRequestFactory() {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofSeconds(5));
    return factory;
  }

  @SuppressWarnings("PMD.CyclomaticComplexity")
  public HoldResponse createHold(CreateHoldRequest request) {
    String token =
        serviceTokenProvider != null ? serviceTokenProvider.generateServiceToken() : null;
    try {
      var builder =
          restClient
              .post()
              .uri("/v1/loyalty/holds")
              .contentType(MediaType.APPLICATION_JSON)
              .body(request);
      if (token != null && !token.isBlank()) {
        builder.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token);
      }
      return builder.retrieve().body(HoldResponse.class);
    } catch (HttpClientErrorException.Forbidden ex) {
      throw new ForbiddenException(extractErrorDetail(ex, "Éligibilité cadeau refusée."));
    } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.NotFound ex) {
      throw new IllegalArgumentException(
          extractErrorDetail(ex, "Impossible de réserver le cadeau fidélité."));
    } catch (RestClientException ex) {
      throw new IllegalStateException(
          "Impossible de joindre le service fidélité pour réserver les points.", ex);
    }
  }

  public void releaseHold(String orderReference) {
    if (orderReference == null || orderReference.isBlank()) {
      return;
    }
    String token =
        serviceTokenProvider != null ? serviceTokenProvider.generateServiceToken() : null;
    try {
      var builder = restClient.delete().uri("/v1/loyalty/holds/{orderReference}", orderReference);
      if (token != null && !token.isBlank()) {
        builder.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token);
      }
      builder.retrieve().toBodilessEntity();
    } catch (RestClientException ex) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Failed to release loyalty hold for order {}: {}", orderReference, ex.getMessage());
      }
    }
  }

  private String extractErrorDetail(HttpClientErrorException ex, String fallback) {
    String body = ex.getResponseBodyAsString();
    if (body == null || body.isBlank()) {
      return fallback;
    }
    int detailIdx = body.indexOf("\"detail\":\"");
    if (detailIdx >= 0) {
      int start = detailIdx + 10;
      int end = body.indexOf('"', start);
      if (end > start) {
        return body.substring(start, end);
      }
    }
    return fallback;
  }

  public record CreateHoldRequest(
      String orderReference, String userId, Instant expiresAt, List<HoldItemRequest> items) {}

  public record HoldItemRequest(UUID storeUid, UUID giftUid, Integer quantity) {}

  public record HoldResponse(
      UUID uid,
      String orderReference,
      String userId,
      java.math.BigDecimal totalPoints,
      String status,
      Instant expiresAt,
      List<HoldItemResponse> items) {}

  public record HoldItemResponse(
      UUID storeUid, UUID giftUid, String giftName, int unitPointsCost, int quantity) {}
}
