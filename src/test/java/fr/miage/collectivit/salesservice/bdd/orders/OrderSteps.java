package fr.miage.collectivit.salesservice.bdd.orders;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.miage.collectivit.salesservice.bdd.common.AbstractScenario;
import fr.miage.collectivit.salesservice.bdd.common.ScenarioContext;
import fr.miage.collectivit.salesservice.client.StoreClient;
import fr.miage.collectivit.salesservice.dto.CreateOrderItemRequest;
import fr.miage.collectivit.salesservice.dto.CreateOrderRequest;
import fr.miage.collectivit.salesservice.dto.UpdateOrderStatusRequest;
import fr.miage.collectivit.salesservice.model.Order;
import fr.miage.collectivit.salesservice.model.OrderItem;
import fr.miage.collectivit.salesservice.model.OrderStatus;
import fr.miage.collectivit.salesservice.repository.OrderRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matchers;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;

@RequiredArgsConstructor
public class OrderSteps extends AbstractScenario {

  private static final String ORDERS_ENDPOINT = "/v1/sales/orders";
  private static final String HEADER_AUTHORIZATION = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final UUID STORE_1_UID = UUID.fromString("11111111-1111-1111-1111-111111111001");
  private static final UUID PRODUCT_101_UID =
      UUID.fromString("22222222-2222-2222-2222-222222222101");
  private static final UUID PRODUCT_201_UID =
      UUID.fromString("22222222-2222-2222-2222-222222222201");
  private static final UUID PRODUCT_10_UID =
      UUID.fromString("22222222-2222-2222-2222-222222222010");

  private final WebApplicationContext webApplicationContext;
  private final ScenarioContext scenarioContext;
  private final OrderRepository orderRepository;
  private final StoreClient storeClient;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;
  private MockRestServiceServer mockStoreServer;
  private String currentOrderReference;

  @Before("@orders")
  public void setUpScenario() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8089");
    mockStoreServer = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    mockStoreServer
        .expect(ExpectedCount.manyTimes(), requestTo(Matchers.containsString("/stock")))
        .andRespond(withSuccess("{\"status\":\"OK\"}", MediaType.APPLICATION_JSON));
    mockStoreServer
        .expect(ExpectedCount.manyTimes(), requestTo(Matchers.containsString("/v1/stores/me")))
        .andRespond(
            withSuccess(
                "[{\"uid\":\"" + STORE_1_UID + "\",\"name\":\"Store 1\"}]",
                MediaType.APPLICATION_JSON));
    mockStoreServer
        .expect(
            ExpectedCount.manyTimes(),
            requestTo(Matchers.containsString("/products/" + PRODUCT_101_UID)))
        .andRespond(
            withSuccess(
                "{\"uid\":\"" + PRODUCT_101_UID + "\",\"price\":1.35}",
                MediaType.APPLICATION_JSON));
    mockStoreServer
        .expect(
            ExpectedCount.manyTimes(),
            requestTo(Matchers.containsString("/products/" + PRODUCT_201_UID)))
        .andRespond(
            withSuccess(
                "{\"uid\":\"" + PRODUCT_201_UID + "\",\"price\":22.00}",
                MediaType.APPLICATION_JSON));
    mockStoreServer
        .expect(ExpectedCount.manyTimes(), requestTo(Matchers.containsString("/products/")))
        .andRespond(withSuccess("{\"price\":10.00}", MediaType.APPLICATION_JSON));
    storeClient.setRestClient(builder.build());
  }

  @Given(
      "a customer creates a multi-store order with {int} items from store {string} at {string} EUR and {int} item from store {string} at {string} EUR")
  public void givenCustomerCreatesMultiStoreOrder(
      int qty1, String storeUid1Str, String price1, int qty2, String storeUid2Str, String price2)
      throws Exception {
    UUID storeUid1 = UUID.fromString(storeUid1Str);
    UUID storeUid2 = UUID.fromString(storeUid2Str);
    CreateOrderItemRequest item1 =
        new CreateOrderItemRequest(
            storeUid1,
            "Store " + storeUid1,
            PRODUCT_101_UID,
            "Product Store 1",
            new BigDecimal(price1),
            qty1);
    CreateOrderItemRequest item2 =
        new CreateOrderItemRequest(
            storeUid2,
            "Store " + storeUid2,
            PRODUCT_201_UID,
            "Product Store 2",
            new BigDecimal(price2),
            qty2);

    CreateOrderRequest request =
        new CreateOrderRequest(List.of(item1, item2), "CLI-BDD-01", "bdd-customer@example.com");

    var requestBuilder =
        post(ORDERS_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request));

    if (scenarioContext.getJwtToken() != null) {
      requestBuilder.header(HEADER_AUTHORIZATION, BEARER_PREFIX + scenarioContext.getJwtToken());
    }

    ResultActions result = mockMvc.perform(requestBuilder);
    scenarioContext.setLastResult(result);
  }

  @When("an order is created with an empty items list")
  public void whenOrderCreatedWithEmptyItems() throws Exception {
    CreateOrderRequest request = new CreateOrderRequest(List.of(), null, null);

    var requestBuilder =
        post(ORDERS_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request));

    if (scenarioContext.getJwtToken() != null) {
      requestBuilder.header(HEADER_AUTHORIZATION, BEARER_PREFIX + scenarioContext.getJwtToken());
    }

    ResultActions result = mockMvc.perform(requestBuilder);
    scenarioContext.setLastResult(result);
  }

  @Given("product {string} in store {string} has insufficient stock")
  public void givenProductHasInsufficientStock(String productUidStr, String storeUidStr) {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8089");
    mockStoreServer = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    mockStoreServer
        .expect(
            ExpectedCount.manyTimes(),
            requestTo(
                Matchers.containsString(
                    "/stores/" + storeUidStr + "/products/" + productUidStr + "/stock")))
        .andRespond(
            withBadRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"Insufficient Stock\"}"));
    mockStoreServer
        .expect(ExpectedCount.manyTimes(), requestTo(Matchers.containsString("/products/")))
        .andRespond(withSuccess("{\"price\":10.00}", MediaType.APPLICATION_JSON));
    storeClient.setRestClient(builder.build());
  }

  @When(
      "an order is created with {int} item of product {string} from store {string} at {string} EUR")
  public void whenOrderCreatedWithSingleItem(
      int qty, String productUidStr, String storeUidStr, String unitPrice) throws Exception {
    UUID storeUid = UUID.fromString(storeUidStr);
    UUID productUid = UUID.fromString(productUidStr);
    CreateOrderItemRequest item =
        new CreateOrderItemRequest(
            storeUid,
            "Store " + storeUid,
            productUid,
            "Product " + productUid,
            new BigDecimal(unitPrice),
            qty);

    CreateOrderRequest request =
        new CreateOrderRequest(List.of(item), "CLI-BDD-01", "client@bdd.fr");

    var requestBuilder =
        post(ORDERS_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request));

    if (scenarioContext.getJwtToken() != null) {
      requestBuilder.header(HEADER_AUTHORIZATION, BEARER_PREFIX + scenarioContext.getJwtToken());
    }

    ResultActions result = mockMvc.perform(requestBuilder);
    scenarioContext.setLastResult(result);
  }

  @Given("an existing order with status {string}")
  public void givenExistingOrderWithStatus(String initialStatus) {
    String ref = "CMD-CC-BDD-" + java.util.UUID.randomUUID();
    Order order =
        Order.builder()
            .orderReference(ref)
            .customerId("cust-bdd")
            .customerEmail("client@bdd.fr")
            .totalAmount(new BigDecimal("15.00"))
            .currency("EUR")
            .status(OrderStatus.valueOf(initialStatus))
            .expiresAt(Instant.now().plusSeconds(1800))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .items(new ArrayList<>())
            .build();

    OrderItem item =
        OrderItem.builder()
            .order(order)
            .storeUid(STORE_1_UID)
            .storeName("Store 1")
            .productUid(PRODUCT_10_UID)
            .productName("Item")
            .unitPrice(new BigDecimal("15.00"))
            .quantity(1)
            .totalPrice(new BigDecimal("15.00"))
            .build();
    order.addItem(item);

    Order saved = orderRepository.save(order);
    currentOrderReference = saved.getOrderReference();
  }

  @When("the order status is updated to {string}")
  public void whenOrderStatusIsUpdated(String targetStatus) throws Exception {
    UpdateOrderStatusRequest patchRequest = new UpdateOrderStatusRequest(targetStatus);

    var requestBuilder =
        patch(ORDERS_ENDPOINT + "/" + currentOrderReference + "/status")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(patchRequest));

    if (scenarioContext.getJwtToken() != null) {
      requestBuilder.header(HEADER_AUTHORIZATION, BEARER_PREFIX + scenarioContext.getJwtToken());
    }

    ResultActions result = mockMvc.perform(requestBuilder);
    scenarioContext.setLastResult(result);
  }
}
