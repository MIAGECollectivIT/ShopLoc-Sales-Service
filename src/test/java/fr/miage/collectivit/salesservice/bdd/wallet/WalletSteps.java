package fr.miage.collectivit.salesservice.bdd.wallet;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.miage.collectivit.salesservice.bdd.common.AbstractScenario;
import fr.miage.collectivit.salesservice.bdd.common.ScenarioContext;
import fr.miage.collectivit.salesservice.client.BankGatewayClient;
import fr.miage.collectivit.salesservice.dto.WalletReloadRequest;
import io.cucumber.java.Before;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

@RequiredArgsConstructor
public class WalletSteps extends AbstractScenario {

  private static final String WALLET_ENDPOINT = "/v1/sales/wallet";
  private static final String HEADER_AUTHORIZATION = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final WebApplicationContext webApplicationContext;
  private final ScenarioContext scenarioContext;
  private final BankGatewayClient bankGatewayClient;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Before("@wallet")
  public void setUpScenario() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    RestClient.Builder builder = RestClient.builder().baseUrl(BANK_GATEWAY_BASE_URL);
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
    bankGatewayClient.setRestClient(builder.build());
    mockServer
        .expect(ExpectedCount.manyTimes(), requestTo(BANK_GATEWAY_AUTHORIZE_URL))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(buildBankGatewayApprovedJson(), MediaType.APPLICATION_JSON));
  }

  @When("the client consults their wallet balance")
  public void whenClientConsultsWalletBalance() throws Exception {
    ResultActions resultActions =
        mockMvc.perform(
            get(WALLET_ENDPOINT)
                .header(HEADER_AUTHORIZATION, BEARER_PREFIX + scenarioContext.getJwtToken())
                .contentType(MediaType.APPLICATION_JSON));
    scenarioContext.setLastResult(resultActions);
  }

  @When("the client reloads their wallet with amount {string} EUR")
  public void whenClientReloadsWalletWithAmount(String amountStr) throws Exception {
    BigDecimal amount = new BigDecimal(amountStr);
    WalletReloadRequest request = new WalletReloadRequest(amount, "tok_visa_4242");

    ResultActions resultActions =
        mockMvc.perform(
            post(WALLET_ENDPOINT + "/reload")
                .header(HEADER_AUTHORIZATION, BEARER_PREFIX + scenarioContext.getJwtToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    scenarioContext.setLastResult(resultActions);
  }

  @When("the client requests their wallet transaction history")
  public void whenClientRequestsTransactionHistory() throws Exception {
    ResultActions resultActions =
        mockMvc.perform(
            get(WALLET_ENDPOINT + "/transactions")
                .header(HEADER_AUTHORIZATION, BEARER_PREFIX + scenarioContext.getJwtToken())
                .contentType(MediaType.APPLICATION_JSON));
    scenarioContext.setLastResult(resultActions);
  }
}
