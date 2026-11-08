package fr.miage.collectivit.salesservice.client;

import fr.miage.collectivit.salesservice.dto.PaymentAuthorizationRequest;
import fr.miage.collectivit.salesservice.dto.PaymentAuthorizationResponse;
import fr.miage.collectivit.salesservice.exception.PaymentGatewayException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Setter
@Component
public class BankGatewayClient {

  public static final String AUTHORIZE_PATH = "/api/v1/payments/authorize";
  private static final int MAX_ATTEMPTS = 2;

  private RestClient restClient;

  @Autowired
  public BankGatewayClient(
      @Value("${services.bank-gateway.url:http://external-mock-service:8080}")
          String bankGatewayUrl) {
    this(RestClient.builder().baseUrl(bankGatewayUrl).build());
  }

  public BankGatewayClient(RestClient restClient) {
    this.restClient = restClient;
  }

  public PaymentAuthorizationResponse authorize(PaymentAuthorizationRequest request) {
    RestClientException lastException = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        PaymentAuthorizationResponse response =
            restClient
                .post()
                .uri(AUTHORIZE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PaymentAuthorizationResponse.class);
        if (response == null) {
          throw new PaymentGatewayException("External bank gateway returned an empty payload");
        }
        return response;
      } catch (RestClientException ex) {
        lastException = ex;
        if (log.isWarnEnabled()) {
          log.warn(
              "Bank gateway call failed on attempt {}/{} for reference {}: {}",
              attempt,
              MAX_ATTEMPTS,
              request.reference(),
              ex.getMessage());
        }
      }
    }
    throw new PaymentGatewayException(
        "Unable to reach external bank gateway after " + MAX_ATTEMPTS + " attempts", lastException);
  }
}
