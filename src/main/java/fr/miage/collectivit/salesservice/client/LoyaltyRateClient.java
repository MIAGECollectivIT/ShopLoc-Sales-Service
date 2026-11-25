package fr.miage.collectivit.salesservice.client;

import fr.miage.collectivit.salesservice.dto.MerchantLoyaltyRateDto;
import fr.miage.collectivit.salesservice.security.ServiceTokenProvider;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Setter
@Component
public class LoyaltyRateClient {

  private final ServiceTokenProvider serviceTokenProvider;
  private RestClient restClient;

  @Autowired
  public LoyaltyRateClient(
      ServiceTokenProvider serviceTokenProvider,
      @Value("${services.loyalty.url:http://loyalty-service:8080}") String loyaltyServiceUrl) {
    this.serviceTokenProvider = serviceTokenProvider;
    this.restClient =
        RestClient.builder()
            .baseUrl(loyaltyServiceUrl)
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

  public LoyaltyRateClient(RestClient restClient) {
    this(null, restClient);
  }

  public LoyaltyRateClient(ServiceTokenProvider serviceTokenProvider, RestClient restClient) {
    this.serviceTokenProvider = serviceTokenProvider;
    this.restClient = restClient;
  }

  public MerchantLoyaltyRateDto getStoreLoyaltyRate(UUID storeUid) {
    String token =
        serviceTokenProvider != null ? serviceTokenProvider.generateServiceToken() : null;
    try {
      var requestBuilder = restClient.get().uri("/v1/loyalty/merchants/{storeUid}/rates", storeUid);
      if (token != null && !token.isBlank()) {
        requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      }
      MerchantLoyaltyRateDto rate =
          requestBuilder
              .retrieve()
              .onStatus(
                  HttpStatusCode::is4xxClientError,
                  (request, response) -> {
                    if (log.isDebugEnabled()) {
                      log.debug(
                          "Custom loyalty rate not found for store {} (status {}), using default",
                          storeUid,
                          response.getStatusCode());
                    }
                  })
              .body(MerchantLoyaltyRateDto.class);

      if (rate != null && rate.euroAmount() != null && rate.pointsReward() != null) {
        return rate;
      }
    } catch (RestClientException ex) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Unable to query loyalty rates for store {} from Loyalty Service: {}. Falling back to default rate.",
            storeUid,
            ex.getMessage());
      }
    }
    return MerchantLoyaltyRateDto.defaultForStore(storeUid);
  }
}
