package fr.miage.collectivit.salesservice.bdd.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import fr.miage.collectivit.salesservice.bdd.common.AbstractScenario;
import fr.miage.collectivit.salesservice.bdd.common.ScenarioContext;
import fr.miage.collectivit.salesservice.client.LoyaltyRateClient;
import fr.miage.collectivit.salesservice.model.PhysicalSaleTransaction;
import fr.miage.collectivit.salesservice.repository.PhysicalSaleTransactionRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
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
public class PhysicalSaleSteps extends AbstractScenario {

  private static final String PHYSICAL_SALES_ENDPOINT = "/v1/sales/physical";
  private static final String LOYALTY_SERVICE_BASE_URL = "http://localhost:8089";

  private final WebApplicationContext webApplicationContext;
  private final ScenarioContext scenarioContext;
  private final LoyaltyRateClient loyaltyRateClient;
  private final PhysicalSaleTransactionRepository physicalSaleTransactionRepository;

  private MockMvc mockMvc;

  @Before
  public void setUpScenario() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    physicalSaleTransactionRepository.deleteAll();

    RestClient.Builder builder = RestClient.builder().baseUrl(LOYALTY_SERVICE_BASE_URL);
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
    loyaltyRateClient.setRestClient(builder.build());
    scenarioContext.setMockLoyaltyServer(mockServer);
  }

  @Given("the store {long} has a loyalty rate of {double} EUR for {double} PTS")
  public void givenStoreHasLoyaltyRate(Long storeId, Double euroAmount, Double pointsReward) {
    String expectedUrl = LOYALTY_SERVICE_BASE_URL + "/v1/loyalty/merchants/" + storeId + "/rates";
    String jsonResponse =
        String.format(
            Locale.ROOT,
            """
            {
              "storeId": %d,
              "euroAmount": %.2f,
              "pointsReward": %.1f,
              "isCustom": true
            }
            """,
            storeId,
            euroAmount,
            pointsReward);

    scenarioContext
        .getMockLoyaltyServer()
        .expect(ExpectedCount.manyTimes(), requestTo(expectedUrl))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));
  }

  @Given("the loyalty service is unavailable with 503 for store {long}")
  public void givenLoyaltyServiceIsUnavailableWith503(Long storeId) {
    String expectedUrl = LOYALTY_SERVICE_BASE_URL + "/v1/loyalty/merchants/" + storeId + "/rates";
    scenarioContext
        .getMockLoyaltyServer()
        .expect(ExpectedCount.manyTimes(), requestTo(expectedUrl))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
  }

  @When(
      "an in-store physical sale is recorded for store {long} client code {string} and amount {string}")
  public void whenInStorePhysicalSaleIsRecorded(Long storeId, String clientCode, String amount)
      throws Exception {
    String payload =
        String.format(
            Locale.ROOT,
            """
            {
              "storeId": %d,
              "clientCode": "%s",
              "amount": %s,
              "currency": "EUR",
              "paymentMethod": "CASH"
            }
            """,
            storeId,
            clientCode,
            amount);

    MockHttpServletRequestBuilder requestBuilder =
        post(PHYSICAL_SALES_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(payload);

    if (scenarioContext.getJwtToken() != null) {
      requestBuilder.header(HEADER_AUTHORIZATION, BEARER_PREFIX + scenarioContext.getJwtToken());
    }

    scenarioContext.setLastResult(mockMvc.perform(requestBuilder));
  }

  @And("a physical sale transaction for client {string} is persisted with points {string}")
  public void andPhysicalSaleTransactionIsPersisted(String clientCode, String expectedPoints) {
    List<PhysicalSaleTransaction> transactions =
        physicalSaleTransactionRepository.findByClientCodeOrderByCreatedAtDesc(clientCode);

    assertThat(transactions).isNotEmpty();
    PhysicalSaleTransaction saved = transactions.getFirst();
    assertThat(saved.getPointsEarned()).isEqualByComparingTo(new BigDecimal(expectedPoints));
    assertThat(saved.getStatus()).isEqualTo("COMPLETED");
  }
}
