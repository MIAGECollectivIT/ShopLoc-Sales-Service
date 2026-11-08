package fr.miage.collectivit.salesservice.bdd.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import fr.miage.collectivit.salesservice.bdd.common.AbstractScenario;
import fr.miage.collectivit.salesservice.bdd.common.ScenarioContext;
import fr.miage.collectivit.salesservice.client.BankGatewayClient;
import fr.miage.collectivit.salesservice.model.PaymentTransaction;
import fr.miage.collectivit.salesservice.repository.PaymentTransactionRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

@RequiredArgsConstructor
public class PaymentSteps extends AbstractScenario {

  private final WebApplicationContext webApplicationContext;
  private final ScenarioContext scenarioContext;
  private final BankGatewayClient bankGatewayClient;
  private final PaymentTransactionRepository paymentTransactionRepository;

  private MockMvc mockMvc;

  @Before
  public void setUpScenario() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    paymentTransactionRepository.deleteAll();

    RestClient.Builder builder = RestClient.builder().baseUrl(BANK_GATEWAY_BASE_URL);
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
    bankGatewayClient.setRestClient(builder.build());
    scenarioContext.setMockBankServer(mockServer);
  }

  @Given("the external bank gateway approves payments under rule RG001")
  public void givenExternalBankGatewayApprovesPayments() {
    scenarioContext
        .getMockBankServer()
        .expect(ExpectedCount.manyTimes(), requestTo(BANK_GATEWAY_AUTHORIZE_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(buildBankGatewayApprovedJson(), MediaType.APPLICATION_JSON));
  }

  @Given("the external bank gateway fails once with 503 before approving on retry")
  public void givenExternalBankGatewayFailsOnceBeforeApprovingOnRetry() {
    scenarioContext
        .getMockBankServer()
        .expect(requestTo(BANK_GATEWAY_AUTHORIZE_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    scenarioContext
        .getMockBankServer()
        .expect(requestTo(BANK_GATEWAY_AUTHORIZE_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(buildBankGatewayApprovedJson(), MediaType.APPLICATION_JSON));
  }

  @Given("the external bank gateway is completely unavailable with 503")
  public void givenExternalBankGatewayIsCompletelyUnavailable() {
    scenarioContext
        .getMockBankServer()
        .expect(ExpectedCount.manyTimes(), requestTo(BANK_GATEWAY_AUTHORIZE_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
  }

  @When(
      "a payment authorization is requested for amount {string} currency {string} reference {string} and token {string}")
  public void whenPaymentAuthorizationIsRequested(
      String amount, String currency, String reference, String token) throws Exception {
    String payload =
        """
        {
          "amount": %s,
          "currency": "%s",
          "reference": "%s",
          "paymentToken": "%s"
        }
        """
            .formatted(amount, currency, reference, token);

    MockHttpServletRequestBuilder requestBuilder =
        post(PAYMENT_AUTHORIZE_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(payload);

    if (scenarioContext.getJwtToken() != null) {
      requestBuilder.header(HEADER_AUTHORIZATION, BEARER_PREFIX + scenarioContext.getJwtToken());
    }

    scenarioContext.setLastResult(mockMvc.perform(requestBuilder));
  }

  @When("the payment transaction is requested for reference {string}")
  public void whenPaymentTransactionIsRequestedForReference(String reference) throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        get(PAYMENT_BY_REFERENCE_ENDPOINT, reference).accept(MediaType.APPLICATION_JSON);

    if (scenarioContext.getJwtToken() != null) {
      requestBuilder.header(HEADER_AUTHORIZATION, BEARER_PREFIX + scenarioContext.getJwtToken());
    }

    scenarioContext.setLastResult(mockMvc.perform(requestBuilder));
  }

  @Then(
      "a payment transaction with reference {string} is persisted in database with status {string}")
  public void thenPaymentTransactionIsPersistedInDatabase(String reference, String expectedStatus) {
    Optional<PaymentTransaction> saved =
        paymentTransactionRepository.findFirstByReferenceOrderByCreatedAtDesc(reference);
    assertThat(saved).isPresent();
    assertThat(saved.get().getStatus()).isEqualTo(expectedStatus);
    assertThat(saved.get().isApprovedUnderRg001()).isTrue();
    assertThat(saved.get().getAuthorizationCode()).isNotBlank();
  }
}
