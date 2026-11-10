package fr.miage.collectivit.salesservice.client;

import fr.miage.collectivit.salesservice.dto.MerchantLoyaltyRateDto;
import java.time.Duration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Setter
@Component
public class LoyaltyRateClient {

  private RestClient restClient;

  @Autowired
  public LoyaltyRateClient(
      @Value("${services.loyalty.url:http://loyalty-service:8080}") String loyaltyServiceUrl) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(3));
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    this.restClient =
        RestClient.builder().baseUrl(loyaltyServiceUrl).requestFactory(requestFactory).build();
  }

  public LoyaltyRateClient(RestClient restClient) {
    this.restClient = restClient;
  }

  public MerchantLoyaltyRateDto getStoreLoyaltyRate(Long storeId) {
    try {
      MerchantLoyaltyRateDto rate =
          restClient
              .get()
              .uri("/v1/loyalty/merchants/{storeId}/rates", storeId)
              .retrieve()
              .onStatus(
                  HttpStatusCode::is4xxClientError,
                  (request, response) -> {
                    if (log.isDebugEnabled()) {
                      log.debug(
                          "Custom loyalty rate not found for store {} (status {}), using default",
                          storeId,
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
            storeId,
            ex.getMessage());
      }
    }
    return MerchantLoyaltyRateDto.defaultForStore(storeId);
  }
}
